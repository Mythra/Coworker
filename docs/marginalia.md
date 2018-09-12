# Marginalia #

Coworker comes built in with Marginalia for it's own code, and is seperated enough
from the core codebase you can use it in your own code if you wish. Marginalia
"annotates" your SQL Queries to include the Class/Function/Line Number that called
the SQL Query. This way you can analyze how a specific class performs over time solely
by looking at your SQL Logs.

So instead of seeing a query that looks like:

```sql
SELECT * FROM really_large_table;
```

You would instead see:

```sql
SELECT * FROM really_large_table /* Class: com.company.app.data.ClassOne, Function: largeAction, Line: 84 */;
```

CoWorker finds out this info by throwing an exception, and looking at the stack trace.
Since this is quite an expensive operation, we keep a local "cache" (or hashmap) so that
way queries with the "same id" don't have to be looked up twice. Unless the cache is cleared.

It should also be noted that the comment comes _before_ the semicolon. So when you call the "AddMarginalia"
function, you ***must*** pass in a query that does not currently end in a semi-colon. We prepend
the class info _before_ the semicolon since _sometimes_ JDBC can think a query with a comment
at the end is returning two result sets instead of one, and we don't want to cause exceptions to be
thrown simply by calling AddMarginalia.
