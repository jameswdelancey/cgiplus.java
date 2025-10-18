
```
.
├── Makefile
├── lib/                # gets json.jar on first run
├── build/              # compiled classes + job output
├── static/
│   └── index.html      # landing page (jQuery UI)
└── src/
    ├── app/
    │   ├── Main.java
    │   ├── QueryUtil.java
    │   └── JobService.java
    ├── domain/
    │   ├── Job.java
    │   └── JobState.java
    ├── ports/
    │   └── RouteExecutorPort.java
    ├── adapters/
    │   ├── in/http/HttpServerAdapter.java
    │   └── out/process/MakeExecutor.java
    └── routes/
        └── api/
            ├── Echo.java
            └── LongDemo.java
```
