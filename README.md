# watch-do

Run commands when files or directories change!


## Building

lein jar


## Installation

Download from https://github.com/bivory/watch-do

## Usage

    $ java -jar watch-do-0.1.0.jar [args]

## Options

--watch <file.clj>
   A file that contains the groups of files to watch and the associated commands to run when a file is modified. The file should have the following format:
[{:cmd "command" :cmd-opts ["opt1" "opt2"] :files ["file1" "file2" ...]} ...]

## Examples

    $ java -jar watch-do-0.1.0.jar --watch example.clj

## License

Copyright © 2013 Bryan Ivory

Distributed under the Eclipse Public License, the same as Clojure.
