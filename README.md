<img src="src/main/resources/META-INF/pluginIcon.svg" width="120" height="120" alt="icon"/>

# Ataman

[![JetBrains IntelliJ Plugins](https://img.shields.io/jetbrains/plugin/v/17567-ataman?label=version)](https://plugins.jetbrains.com/plugin/17567-ataman)
[![jb downloads](https://img.shields.io/jetbrains/plugin/d/17567-ataman?label=downloads)](https://plugins.jetbrains.com/plugin/17567-ataman)


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

This repo uses [gradle-intellij-plugin](https://github.com/JetBrains/gradle-intellij-plugin/) for building.
To build plugin, use this command:

```
./gradlew buildPlugin
```
For more advanced usecases, please refer to gradle-intellij-plugin documentation.

## License

This project is distributed under MIT License. Please refer to [LICENSE.txt](LICENSE.txt) for details.
