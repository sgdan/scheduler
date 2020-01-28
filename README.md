# scheduler

Provide simple UI to support self-service starting of resources
in an AWS account. Supported resource types are:

- EC2 instances
- Auto scaling groups
- RDS instances
- DocumentDB clusters

Only resources tagged appropriately will be affected, see
`src/main/resources/application.yml` for the tag settings (suggested
is to use `scheduler-enabled` set to `true`).

## Running

To build and run the docker container, use `make run` then go to
[http://localhost:8080](http://localhost:8080) for the UI.

Credentials for AWS access will be looked up in the default way
by the SDK.
See https://docs.aws.amazon.com/sdk-for-java/v2/developer-guide/credentials.html

## Development

Assumes frontend tools have already been installed locally e.g.

```bash
yarn global add create-elm-app
yarn global add elm-test
yarn global add elm
```

1. Run the back end using `make backend-dev`
2. Run the front end using `make frontend-dev`
3. Go to [http://localhost:3000](http://localhost:3000) in your browser to
   test. The UI will automatically reload when front end code is changed.

For front end unit tests run `make frontend-test`.

- Front end is written in [Elm](https://elm-lang.org/)
- Using [Create Elm App](https://github.com/halfzebra/create-elm-app)
- Icon generated with [https://favicon.io/favicon-generator/](https://favicon.io/favicon-generator/)
- Back end using [Micronaut](https://micronaut.io/) and [Kotlin](https://kotlinlang.org/)
