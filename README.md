<img src="src/main/resources/META-INF/pluginIcon.svg" width="120" height="120" alt="icon" align="left"/>

# Ataman

> Ataman - an elected leader of the Cossack troops and settlements

Ataman is an Intellij Idea plugin for using leader key for bindings (almost like in Spacemacs)

## Rationale

Intellij Idea is notorious for its tricky keybindings involving multiple modifiers and F1-F12 keys. Another approach of
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
the `Ataman: Transparent Leader Key` action and add this line

```
:nnoremap <Space> :action LeaderAction<cr>
```

to your `~/.ideavimrc` file. This way leader key will work unless you are entering text anywhere

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
      f { actionId: ReformatCode, description: Reformat code }
      c { # you can nest arbitrary amount of tree groups
        description: Compile/Run...
        bindings { 
          a { actionId: RunAnything, description: Run Anything... }
          r { actionId: ReRun, description: Rerun last build }
        }
        # actionId: ... -- error! do not mix actionId and bindings clause together! 
      }
    }
  }
}
```

## Building from source

This repo uses [gradle-intellij-plugin](https://github.com/JetBrains/gradle-intellij-plugin/) for building.
To build plugin, use this command:

```
./gradlew buildPlugin
```
For more advanced usecases, please refer to gradle-intellij-plugin documentation.

## License

This project is distributed under MIT License. Please refer to [LICENSE.txt](LICENSE.txt) for details.
