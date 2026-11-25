package io.github.tycho991.kombu.ingredientparser

/**
 * The kind of unit in a measured ingredient line, independent of the language it was written in.
 *
 * The parser never returns display text for a unit; the app maps these identifiers to its own string
 * resources. The original spelling is available as `unitText` on the parsed line.
 */
public enum class IngredientUnit {
    /** Cup (`cup`, `Tasse`). */
    CUP,

    /** US gallon. */
    GALLON,

    /** Ounce (`oz`, `Unze`). */
    OUNCE,

    /** Pint. */
    PINT,

    /** Pound (`lb`, `Pfund`). */
    POUND,

    /** Quart. */
    QUART,

    /** Tablespoon (`tbsp`, `EL`, `Esslöffel`). */
    TABLESPOON,

    /** Coffee spoon (`Kaffeelöffel`, `KL`). */
    COFFEESPOON,

    /** Teaspoon (`tsp`, `TL`, `Teelöffel`). */
    TEASPOON,

    /** Gram. */
    GRAM,

    /** Kilogram. */
    KILOGRAM,

    /** Litre. */
    LITER,

    /** Centilitre. */
    CENTILITER,

    /** Milligram. */
    MILLIGRAM,

    /** Millilitre. */
    MILLILITER,

    /** Package (`pkg`, `Packung`). */
    PACKAGE,

    /** Small packet or sachet (`packet`, `Päckchen`, `Pck.`, `Tütchen`). */
    PACKET,

    /** Stick (`stick`, `Stange`). */
    STICK,

    /** Piece (`pcs`, `Stück`). */
    PIECE,

    /** Pinch (`Prise`). */
    PINCH,

    /** Clove, for example of garlic (`Zehe`). */
    CLOVE,

    /** Slice (`Scheibe`). */
    SLICE,

    /** Can or tin (`can`, `Dose`). */
    CAN,

    /** Box (`Schachtel`). */
    BOX,

    /** Bag (`bag`, `Beutel`, `Tüte`). */
    BAG,

    /** Leaf (`leaf`, `Blatt`). */
    LEAF,

    /** Sprig (`sprig`, `Zweig`). */
    SPRIG,

    /** Generic unit (`unit`). */
    UNIT,

    /** Dose (`Dosis`). */
    DOSE,

    /** Drop (`drop`, `Tropfen`). */
    DROP,

    /** Cube (`cube`, `Würfel`). */
    CUBE,

    /** Measuring cup (`measuring cup`, `Messbecher`). */
    DOSING_CUP,

    /** Bunch (`bunch`, `Bund`). */
    BUNCH,

    /** Stalk (`Stängel`, `Stiel`). */
    STALK,

    /** Dash or splash (`dash`, `Spritzer`, `Schuss`). */
    DASH,

    /** Bulb or tuber (`Knolle`). */
    BULB,

    /** Handful (`handful`, `Handvoll`). */
    HANDFUL,

    /** Tub, for example of cream (`Becher`). */
    TUB,

    /** Scoop or ball (`Kugel`). */
    SCOOP,
}
