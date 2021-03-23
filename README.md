![GitHub release (latest by date)](https://img.shields.io/github/v/release/hmrc/file-transmission-synchronous) ![GitHub code size in bytes](https://img.shields.io/github/languages/code-size/hmrc/file-transmission-synchronous)

# file-transmission-synchronous

Backend microservice providing an API to transfer a file from the Upscan bucket to the PEGA/Documentum via an EIS endpoint `/cpr/filetransfer/caseevidence/v1`.

## API

### Transfer File

Requests file transfer from some `downloadUrl` returned by Upscan callback to `/cpr/filetransfer/caseevidence/v1`.

Method | Path | Description | Authorization
---|---|---|---
`POST` | `/transfer-file` | transfer file to the PEGA/Documentum system via EIS | any GovernmentGateway authorized user

Header | Description
---|---
`x-correlation-id` | message correlation UUID (optional)

Response status | Description
---|---
202| when file transfer successful
400| when payload invalid or has not passed the validation

Example request payload:

    {
        "conversationId": "074c3823-c941-417e-a08b-e47b08e9a9b7",
        "caseReferenceNumber": "Risk-123",
        "applicationName": "Route1",
        "upscanReference": "XYZ0123456789",
        "downloadUrl": "https://s3.amazonaws.com/bucket/9d9e1444-2555-422e-b251-44fd2e85530a",
        "fileName": "test.jpeg",
        "fileMimeType": "image/jpeg",
        "fileSize": 12345,  // optional
        "checksum": "a38d7dd155b1ec9703e5f19f839922ad5a1b0aa4f255c6c2b03e61535997d757",
        "batchSize": 1,
        "batchCount": 1
    }

Example 400 error response payload

    {
        "correlationId" : "7fedc2d5-1bba-434b-87e6-4d4ec1757e31",
        "error" : {
            "errorCode" : "400",
            "errorMessage" : "invalid case reference number"
        }
    }     


## Running the tests

    sbt test it:test

## Running the tests with coverage

    sbt clean coverageOn test it:test coverageReport

## Running the app locally

    sm --start FILE_TRANSMISSION_SYNCHRONOUS
    sm --start TRADER_SERVICES_ROUTE_ONE_STUB
    sbt run

It should then be listening on port 10003

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
