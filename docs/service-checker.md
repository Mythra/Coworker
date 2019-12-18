# Service Checking #

Service checking is useful for job servers which may scale up/down frequently. For example
being able to stick your jobs in an Auto Scaling Group, that scales up/down without a user
having to manually add/remove servers. Not to mention, just handling failure in a sane way
without having to manually intervene whenever a piece of hardware dies.

To do this, we recommend having Consul Agents running on each job box, talking to your
[Consul](https://consul.io) deployment. The consul-agent provides a lot of resiliency
when talking to consul right out of the box. Not to mention general best practices.

## Setting up the Consul Health Check ##

Coworker will not manage your health checks for you. This is so you can configure your
consul health check in any way you see fit. You know your deployment better than us.
However an example service check might look something like this:

```json
{
  "service": {
    "name": "coworker",
    "address": "my.local.ipv4",
    "port": 8000
  }
}
```

This simple service check should be run on node startup, and the consul-agent will
handle the rest. Next inside your code we can go ahead and create a service checker:


***Kotlin:***

```kotlin
import io.kungfury.coworker.consul.ServiceChecker

import java.util.Optional

fun getServiceChecker(): ServiceChecker {
  return ServiceChecker(
    // Consul URI
    "http://localhost:8500",
    // The service name we chose in our definition.
    "coworker",
    // The token to use when authenticating to consul if needed.
    Optional.empty(),
    // No timeout
    null
  )
}
```

***Java:***

```java
import io.kungfury.coworker.consul.ServiceChecker;

import java.util.Optional;

public class Utils {
    static ServiceChecker getServiceChecker() {
        return new ServiceChecker(
            // The Consul URI.
            "http://localhost:8500",
            // The service name.
            "coworker",
            // No token for local agent
            Optional.empty(),
            // No timeout
            null
        );
    }
}
```

Then pass it into your CoworkerManager, and Coworker will automatically ensure failed
nodes work gets rescheduled without any human intervention.

## Custom ServiceChecker ##

A user-provided implementation of a service checker may be provided.  It
should subclass the `ServiceChecker` interface, and return strings which
represent nodes.  If identifiers other than host IP addresses (the default)
are used, the `coworkerManger.Start()` must be provided with a `nodeIdentifier`.

Example:  If Amazon AWS instance-ids are used, which look like 'i-123abd31',
a SerciceChecker may be written to find all running instances in a specific
autoscsaling group.  The nodeIdentifier would then be an instance-id of the
local instance.

Other discovery mechanisms such as Netflix's Eureka or etcd could be implemented,
using their specific format of identifer.
