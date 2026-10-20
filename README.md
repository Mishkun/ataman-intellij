<img src="src/main/resources/META-INF/pluginIcon.svg" width="120" height="120" alt="icon"/>

# Ataman

[![JetBrains IntelliJ Plugins](https://img.shields.io/jetbrains/plugin/v/17567-ataman?label=version)](https://plugins.jetbrains.com/plugin/17567-ataman)
[![jb downloads](https://img.shields.io/jetbrains/plugin/d/17567-ataman?label=downloads)](https://plugins.jetbrains.com/plugin/17567-ataman)
[![Build & Test](https://github.com/mishkun/ataman-intellij/workflows/Build%20&%20Test/badge.svg)](https://github.com/mishkun/ataman-intellij/actions)


> Ataman - an elected leader of the Cossack troops and settlements

Ataman is an Intellij Idea plugin for using leader key for bindings (almost like in Spacemacs). Great way to enchance
your
IdeaVim productivity or just to have a more convenient way to access actions, as it is not required.

## Rationale

IntelliJ IDEA is notorious for its tricky keybindings involving multiple modifiers and F1-F12 keys. Another approach of
using `Cmd+Shift+A` command pallete and search for most of the actions, reducing the speed.

There is another way, popularized by Spacemacs and Doom Emacs – leader (or sticky) keys. It works fairly simple – you
choose a combination to use as a leader, e.g. `Ctrl-E`. After you activate leader, next keys can be simply typed one
after another. For example, we can have `Ctrl-E c r` for opening refactoring menu and `Ctrl-E c f` to reformat file.
With this approach keybindings are easier to type and memorize.

This approach could already be done in IntelliJ using IdeaVim and
some [tricks](https://ztlevi.github.io/posts/The-Minimal-Spacemacs-Tweaks-for-Jetbrain-IDES/). Ataman is independent of
your choice to use IdeaVim and works everywhere across Intellij

## Easy setup

Install plugin from Jetbrains Marketplace (or build it yourself as shown below). In your keymap
settings (`Preferences -> Keymap`)
find and bind `Ataman: Leader Key` to the shortcut of your choice. When executed first time, the only binding is to open
your config. Enjoy!

## Advanced setup for IdeaVim users

To use leader key without modifier (e.g. to use SPACE as leader), bind your desired leader key to
the `Ataman: Transparent Leader Key` action and add these lines

```
nnoremap <Space> :action LeaderAction<cr>
vnoremap <Space> :action LeaderAction<cr>
```

to your `~/.ideavimrc` file. This way leader key will work unless you are entering text anywhere

## Other actions

- `Ataman: Open or Create ~/atamanrc.config` - opens your config file in the editor (creates if it does not exist yet)
- `Ataman: Reload ~/.atamanrc.config File` - reloads your config file. Call this action after editing your config
- `Ataman: Repeat Latest Command` - repeats the last command executed by Ataman

## Config structure

Your mappings config is located at `~/.atamanrc.config`. File is
in [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md) format. Suggested style is here:

```hocon
bindings { # always present
  c { # tree of bindings starting with 'c'
    description: Code...
    bindings {
      # some leaves with actions to call
      r { actionId: RefactoringMenu, description: Refactor this... }
      c { # you can nest arbitrary amount of tree groups
        description: Compile/Run...
        bindings { 
          a { actionId: RunAnything, description: Run Anything... }
          r { actionId: ReRun, description: Rerun last build }
        }
        # actionId: ... -- error! do not mix actionId and bindings clause together! 
      }
      # You can use F keys as well
      F12 {actionId: CloseProject, description: Close Project}
      
      # You can also do multiple actions in a row
      f { actionId: [ReformatCode, OptimizeImports], description: Reformat and Optimize Imports }
    }
  }
}

# You can also have ide-specific bindings
# For example, to have different bindings for PyCharm, use its product code
# To find out product code, look at the about screen build info section
# Build #<ide tag will be here>-242.23339.11
PY {
  # This binding will override defaults for PyCharm
  c {
    description: Closing...
    bindings {
      r { actionId: CloseProject, description: Close Project }
    }
  }
  # This binding will be added to the list for PyCharm
  F12 { actionId: CloseProject, description: Close Project }
}
```

You can look at my own config [here](https://gist.github.com/Mishkun/b3fa501f82a5ad1205adf87c89c70031) for more examples

### Finding actionId

To find actionId of the action you want to bind, you can use IdeaVim's action "IdeaVim: Track Action IDs"

## Building from source

This repo uses the [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html).
Use JDK 21 and the checked-in Gradle Wrapper.
To build plugin, use this command:

```
./gradlew buildPlugin
```

For running tests:

```
./gradlew test       # Unit tests
./gradlew uiTests    # UI tests
```

To run the build, unit tests, and binary compatibility checks:

```sh
./gradlew build verifyPlugin
```

The supported range is declared by the two matching literal `sinceBuild = "243"`
and `untilBuild = "261.*"` settings in `build.gradle.kts`: plugin metadata and the
fallback verifier selector. This CI change does not include PR #40's range increase.
Verification on a newer IDE is a forward check, not a declaration of support.

`gradle.properties` configures the compilation platform. The verifier matrix is optional:

- `platformVersion`: fixed IDEA version used to compile and run tests.
- `pluginVerificationVersions`: optional comma-separated exact IDEA versions or build numbers checked by Plugin Verifier; not set by default.

When `pluginVerificationVersions` is present, only those exact IDEA targets are
checked. Without it, CI checks IDEA 2024.3 plus the latest stable IDEA selected
within the literal supported range in `build.gradle.kts`. A later range increase
automatically changes that latest target; there is no fixed default override.

To check a specific IDEA build without changing these defaults:

```sh
./gradlew verifyPlugin -PpluginVerificationVersions=2024.3,2026.1
```

Plugin ZIPs are written to `build/distributions/`; compatibility reports are in
`build/reports/pluginVerifier/`. Plugin Verifier checks binary compatibility;
it does not replace the UI tests or a manual interaction check.

## CI and releases

Pull requests run the build and Plugin Verifier without publishing credentials.
The compatibility workflow runs daily at 08:23 UTC and can also be started manually.
It checks the official IntelliJ IDEA release feed and opens or updates a PR when
a newer platform branch is available. It prefers a current RC, or a newer stable
release when the RC has already been superseded. It does not raise the minimum
supported version or change the compilation platform. It updates both `untilBuild`
literals and adds or updates the optional explicit matrix in `gradle.properties`.
The PR includes verification results and links to reports; failed checks do not
enable automatic merging.
GitHub can disable scheduled workflows in public repositories after 60 days
without repository activity. Re-enable the workflow in Actions if this happens.

The release workflow starts after a push or merge to `master` and releases a patch
version automatically. It can also be started manually with `workflow_dispatch`
for a minor/major release or a retry. It builds and verifies the release, and atomically pushes the release commit
and tag. It then saves the checked ZIP and its checksum in a draft GitHub release,
uploads that ZIP to JetBrains Marketplace, and publishes the draft. Configure the repository secret
`JETBRAINS_MARKETPLACE_TOKEN` before running it. The snapshot workflow publishes
GitHub artifacts only, not Marketplace updates.
The version commit uses `GITHUB_TOKEN` and does not trigger another release.

Compatibility PRs can use the repository's `GITHUB_TOKEN`. For ordinary PR checks
to start without manual approval, optionally configure a GitHub App with repository
contents and pull-request write permissions: set the repository variable `CI_APP_ID`
and secret `CI_APP_PRIVATE_KEY`. The scheduled workflow runs verification itself
even when it falls back to `GITHUB_TOKEN`.

Marketplace upload and GitHub release creation are separate operations. If a run
fails, inspect the version on Marketplace first. If it was not uploaded, retry with
`existing_tag=vMAJOR.MINOR.PATCH` and `retry_publish_confirmed=true`: the workflow
restores and re-verifies the saved ZIP without a version bump. Missing draft assets
can be rebuilt from that exact tag. If Marketplace already accepted the upload,
publish the existing GitHub draft manually instead of uploading again.

## License

This project is distributed under MIT License. Please refer to [LICENSE.txt](LICENSE.txt) for details.
