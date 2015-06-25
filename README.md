# hbase-region-inspector

## Usage

```
usage: hbase-region-inspector QUORUM[/ZKPORT] PORT
```

## Prerequisites

- [NPM](https://www.npmjs.com/)
- [Leiningen](https://github.com/technomancy/leiningen)

```sh
brew install npm leiningen
```

## Development

```sh
make js

# 0.98
make watch
make repl

# 0.94
make watch profile=0.94
make repl profile=0.94
```

## Build

```sh
make profile=0.98
make profile=0.94
```

## License

[MIT](LICENSE)
