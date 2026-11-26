package io.github.tycho991.kombu.ui.recipes.editor

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tycho991.kombu.cookbookclient.RecipeField
import io.github.tycho991.kombu.cookbookclient.RecipeFields
import io.github.tycho991.kombu.cookbookclient.RecipeValues
import io.github.tycho991.kombu.cookbookclient.RemoteRecipe
import io.github.tycho991.kombu.data.recipes.EditResult
import io.github.tycho991.kombu.data.recipes.RecipeChange
import io.github.tycho991.kombu.data.recipes.RecipeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/** A question the editor asks before it saves (spec "Sync updates during editing"). */
sealed interface EditorDialog {
    /** A sync changed [fields], which the user also edited; [current] is the stored version. */
    data class Collision(val fields: Set<RecipeField>, val current: RemoteRecipe) : EditorDialog

    /** The recipe was removed meanwhile; the form can be saved as a new recipe. */
    data object Gone : EditorDialog
}

/** The state of the editor. */
data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = false,
    val form: EditorForm = EditorForm(),
    /** Whether leaving now would lose something the user entered. */
    val hasChanges: Boolean = false,
    val errors: Set<FormError> = emptySet(),
    val dialog: EditorDialog? = null,
    /** The last save failed in the database; the form is kept. */
    val saveFailed: Boolean = false,
    /** The editor is done: saved, discarded, or its recipe is gone; the screen closes. */
    val finished: Boolean = false,
    val categories: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
)

/**
 * The editor for the recipe with [localId], or for a new recipe when it is `null` (design D5 of
 * recipe-browse-edit). The opened version and the form live in [saved], so they survive process death. Saving
 * writes only the fields that differ from the opened version, and checks that no sync changed them meanwhile.
 */
class RecipeEditorViewModel(
    private val localId: Long?,
    private val recipes: RecipeRepository,
    private val saved: SavedStateHandle,
    locale: Locale,
) : ViewModel() {
    private val errors = MutableStateFlow(emptySet<FormError>())
    private val dialog = MutableStateFlow<EditorDialog?>(null)
    private val saveFailed = MutableStateFlow(false)
    private val finished = MutableStateFlow(false)

    private val form: EditorForm get() = saved.get<String>(FORM)?.let(::decodeForm) ?: EditorForm()
    private val opened: RemoteRecipe get() = saved.get<String>(OPENED)?.let(RemoteRecipe::parse) ?: EMPTY

    init {
        if (saved.get<String>(OPENED) == null) viewModelScope.launch { open() }
    }

    /** The state of the screen. */
    val state: StateFlow<EditorUiState> =
        combine(
            combine(saved.getStateFlow<String?>(OPENED, null), saved.getStateFlow<String?>(FORM, null), ::Pair),
            combine(errors, dialog, saveFailed, finished, ::Flags),
            combine(recipes.categories(locale), recipes.keywords(locale), ::Pair),
        ) { (openedText, formText), flags, (categories, keywords) ->
            val current = formText?.let(::decodeForm)
            EditorUiState(
                loading = openedText == null || current == null,
                isNew = localId == null,
                form = current ?: EditorForm(),
                hasChanges = current != null && hasChanges(current),
                errors = flags.errors,
                dialog = flags.dialog,
                saveFailed = flags.saveFailed,
                finished = flags.finished,
                categories = categories,
                keywords = keywords,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), EditorUiState())

    private data class Flags(
        val errors: Set<FormError>,
        val dialog: EditorDialog?,
        val saveFailed: Boolean,
        val finished: Boolean,
    )

    /** Changes the form; errors of the old values are cleared. */
    fun update(change: (EditorForm) -> EditorForm) {
        saved[FORM] = encodeForm(change(form))
        errors.value = emptySet()
        saveFailed.value = false
    }

    /** Validates and saves the form (spec "Only edited fields change"). */
    fun save() {
        viewModelScope.launch { saveForm() }
    }

    /** After a collision: saves the user's values over the ones the sync brought ("save mine anyway"). */
    fun keepMine() {
        val collision = dialog.value as? EditorDialog.Collision ?: return
        saved[OPENED] = collision.current.encode()
        dialog.value = null
        save()
    }

    /** After a collision: puts the sync's values of the colliding fields into the form for the user to check. */
    fun takeTheirs() {
        val collision = dialog.value as? EditorDialog.Collision ?: return
        val theirs = EditorForm.of(RecipeFields.read(collision.current))
        saved[OPENED] = collision.current.encode()
        saved[FORM] = encodeForm(collision.fields.fold(form) { current, field -> current.withField(field, theirs) })
        dialog.value = null
    }

    /** After the recipe was deleted meanwhile: saves the form as a new recipe. */
    fun saveAsNew() {
        dialog.value = null
        viewModelScope.launch {
            val values = form.toValues().values
            guardWrite { recipes.create(RecipeFields.write(opened, editedFields(values), values)) }
        }
    }

    /** Leaves the editor without saving. */
    fun discard() {
        dialog.value = null
        finished.value = true
    }

    private suspend fun open() {
        val recipe = if (localId == null) EMPTY else recipes.recipe(localId).first()?.shown
        if (recipe == null) {
            finished.value = true
            return
        }
        saved[OPENED] = recipe.encode()
        saved[FORM] = encodeForm(EditorForm.of(RecipeFields.read(recipe)))
    }

    private suspend fun saveForm() {
        val current = form
        val result = current.toValues()
        val values = result.values
        val found = result.errors.toMutableSet()
        if (FormError.NAME_REQUIRED !in found && recipes.isNameTaken(values.name.orEmpty(), localId)) {
            found += FormError.NAME_TAKEN
        }
        errors.value = found
        if (found.isNotEmpty()) return
        val fields = editedFields(values)
        when {
            localId == null -> guardWrite { recipes.create(RecipeFields.write(EMPTY, fields, values)) }

            fields.isEmpty() -> finished.value = true

            else -> guardWrite {
                when (val outcome = recipes.edit(localId, opened, RecipeChange(fields, values))) {
                    EditResult.Edited -> Unit
                    is EditResult.Collision -> dialog.value = EditorDialog.Collision(outcome.fields, outcome.current)
                    EditResult.Gone -> dialog.value = EditorDialog.Gone
                }
            }
        }
    }

    /** Runs a database write; it finishes the editor unless a dialog is open, and keeps the form on failure. */
    private suspend fun guardWrite(write: suspend () -> Unit) {
        try {
            write()
            if (dialog.value == null) finished.value = true
        } catch (ignored: SQLException) {
            // The form stays as it is, so nothing typed is lost (design D10); the screen says that saving failed.
            saveFailed.value = true
        }
    }

    /** The fields whose values differ from the opened version, both read through the form. */
    private fun editedFields(values: RecipeValues): Set<RecipeField> =
        values.differingFields(EditorForm.of(RecipeFields.read(opened)).toValues().values)

    private fun hasChanges(current: EditorForm): Boolean =
        if (localId == null) !current.isBlank else editedFields(current.toValues().values).isNotEmpty()

    private companion object {
        const val OPENED = "opened"
        const val FORM = "form"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        val EMPTY = RemoteRecipe(JsonObject(emptyMap()))
        private val json = Json { ignoreUnknownKeys = true }

        fun encodeForm(form: EditorForm): String = json.encodeToString(EditorForm.serializer(), form)

        fun decodeForm(text: String): EditorForm = json.decodeFromString(EditorForm.serializer(), text)
    }
}

/** This form with [field] taken from [other]. */
internal fun EditorForm.withField(field: RecipeField, other: EditorForm): EditorForm = when (field) {
    RecipeField.NAME -> copy(name = other.name)
    RecipeField.DESCRIPTION -> copy(description = other.description)
    RecipeField.CATEGORY -> copy(category = other.category)
    RecipeField.KEYWORDS -> copy(keywords = other.keywords, keywordInput = "")
    RecipeField.SERVINGS -> copy(servings = other.servings)
    RecipeField.PREP_TIME -> copy(prepTime = other.prepTime)
    RecipeField.COOK_TIME -> copy(cookTime = other.cookTime)
    RecipeField.TOTAL_TIME -> copy(totalTime = other.totalTime)
    RecipeField.INGREDIENTS -> copy(ingredients = other.ingredients)
    RecipeField.STEPS -> copy(steps = other.steps)
}
