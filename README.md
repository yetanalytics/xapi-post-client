# xapi-post-client

## Description

This is a client library for POSTing xAPI statements to the specified Learning Record Store (LRS). The function
takes EDN format and outputs JSON format to the LRS.

## Usage

The library includes the function `post-statement` with the parameters `(post-statement endpoint key secret statement)`

`Endpoint`: The URI of the LRS including the host and the port. "/statements" is automatically added to the end of the endpoint.

`Key` and `Secret`: The Key and Secret to the specified LRS.

`Statement`: The EDN formatted xAPI statement to POST

Note: When there are no errors thrown, `post-statement` returns a list of ID of the inserted statement

## Example Usage

```
(def stmt-0
  {"id"     "00000000-0000-4000-8000-000000000000"
   "actor" {"objectType" "Agent"
            "name" "Eva Kim"
            "mbox" "mailto:eva@example.adlnet.gov"}
   "verb" {"id" "http://adlnet.gov/expapi/verbs/voided"
           "display" {"en-US" "voided"}}
   "object" {"objectType" "StatementRef"
             "id" "e3612d97-3900-4bef-92fd-d8db73e79e1b"}})
```

Below is an example of POSTing using `post-statement`:

`(post-statement "http://localhost:8080" "username" "password" stmt-0)`

## Makefile

`make test-unit` runs all the unit-tests in `postclient_test.clj`
