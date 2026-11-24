# Kombu

Kombu is an Android household app built around [Nextcloud Cookbook](https://github.com/nextcloud/cookbook). It keeps your recipes where they already live, in your own Nextcloud, and adds what a household needs around them:

- **Recipes and cooking mode** – browse and edit your Cookbook recipes, fully offline, with a step-by-step cooking mode, timers, ingredient check-off and serving scaling.
- **Recipe import** – share a recipe as text from another app (for example an AI assistant) and review it in the editor before saving.
- **Shopping lists** *(planned)* – shared lists with categories, history and suggestions, stored as CalDAV tasks.
- **Meal plan** *(planned)* – plan what to cook when, stored in a shared CalDAV calendar.
- **Chores** *(planned)* – a needs-based chore planner per room, stored as CalDAV tasks.

Kombu has no server of its own. Everything is stored in your Nextcloud and shared with the people in your household through Nextcloud's own sharing, so other apps can keep reading the same data.

## Status

Early planning. There is no app code yet. Development is spec-driven with [OpenSpec](https://github.com/Fission-AI/OpenSpec); the project context lives in [`openspec/config.yaml`](openspec/config.yaml) and changes will appear under [`openspec/changes/`](openspec/changes/).

The first milestone (v1) covers recipes and the cooking mode. Shopping lists, meal plan and chores follow afterwards.

## Requirements (planned)

- Android
- A Nextcloud instance with the Cookbook app (and Calendar/Tasks for the later features)

## Language

The app is developed in English with German as the first translation. Further translations are welcome later on.

## License

Kombu is free software, licensed under the [GNU General Public License v3.0 or later](LICENSE).

Kombu is an independent project and is not affiliated with or endorsed by Nextcloud GmbH. Nextcloud is a registered trademark of Nextcloud GmbH.
