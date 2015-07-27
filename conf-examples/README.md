Configuration examples
======================

### No authentication

- [insecure.properties](insecure.properties)

Configuration files are not necessary if Kerberos authentication is disabled,
since you can simply pass the ZooKeeper quorum instead as the command-line
argument.

```sh
# With configuration file
./hbase-region-inspector insecure.properties 7777

# ZooKeeper quorum as the argument
./hbase-region-inspector zookeeper.example.com 7777
```

### Authentication with keytab (recommended)

- [secure-keytab.properties](secure-keytab.properties)
- [secure-keytab-jaas.conf](secure-keytab-jaas.conf)
- Kerberos configuration (`/etc/krb5.conf`)
- Kerberos keytab

Manual renewal of ticket is not needed.

### Authentication with ticket cache

- [secure-ticket-cache.properties](secure-ticket-cache.properties)
- [secure-ticket-cache-jaas.conf](secure-ticket-cache-jaas.conf)
- Kerberos configuration (`/etc/krb5.conf`)

In this case, you have to obtain a ticket cache beforehand with `kinit`.

```
> kinit user@HBASE.EXAMPLE.COM
user@HBASE.EXAMPLE.COM's Password:
> ./hbase-region-inspector secure-ticket-cache.properties 7777
```

Also make sure to periodically renew the ticket before it expires.
