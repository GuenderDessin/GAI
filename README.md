# GTok

GTok is a NetBeans Java project that retrieves the TikTok trending feed,
converts the response into relational rows, calculates engagement rates,
and can insert results into SQL Server.

## Requirements

- JDK 21 or newer
- Apache NetBeans (the project was created with NetBeans 28)
- A ScrapeCreators API key

The required Jackson libraries are included in `libs/`, so a newly cloned
copy can be opened and built without manually downloading those JARs.

## API-key setup

The API key is intentionally not stored in this repository. Set it separately
on every computer.

Windows PowerShell:

```powershell
[Environment]::SetEnvironmentVariable(
    "SCRAPE_CREATORS_API_KEY",
    "replace-with-your-key",
    "User"
)
```

Restart NetBeans after setting the variable so the IDE inherits it.

## Build and run

Open the project directory in NetBeans and use **Clean and Build**. The main
class is currently `gtok.GTok`. To run the feed collector directly, run
`gtok.Trending`.

From a command prompt with Ant available:

```text
ant clean jar
```

## Working from multiple computers

1. Pull before starting work.
