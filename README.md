# hbase-region-inspector

## Usage

```
usage: hbase-region-inspector [--read-only --with-meta] ┌ QUORUM[/ZKPORT] ┐ PORT
                                                        └ CONFIG_FILE     ┘
```

To access a secured HBase cluster, you have to prepare the following
configuration files:

- The main properties file
- JAAS login configuration
- Kerberos configuration (usually `/etc/krb5.conf`)
- Kerberos keytab (optional, but recommended)

You can find the examples in [conf-examples](conf-examples/).

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
