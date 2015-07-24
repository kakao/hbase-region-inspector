Configuration examples
======================

### No authentication

- [insecure.properties](insecure.properties)

Configuration files are unnecessary if Kerberos authentication is disabled.
You can simply pass the ZooKeeper quorum instead as the command-line argument.

```sh
> ./hbase-region-inspector insecure.properties 7777
> ./hbase-region-inspector zookeeper.example.com/2181 7777
```

### Authentication with keytab

- [secure-keytab.properties](secure-keytab.properties)
- [secure-keytab-jass.conf](secure-keytab-jass.conf)
- Kerberos keytab

### Authentication with ticket cache

- [secure-ticket-cache.properties](secure-ticket-cache.properties)
- [secure-ticket-cache-jass.conf](secure-ticket-cache-jass.conf)

In this case, you have to obtain a ticket cache beforhand with `kinit`.

```
> kinit user@HBASE.EXAMPLE.COM
user@HBASE.EXAMPLE.COM's Password:
> ./hbase-region-inspector secure-ticket-cache.properties 7777
```

Also, make sure to periodically renew the ticket before it expires.
