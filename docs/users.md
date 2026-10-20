User profiles & authentication
==============================

Some behavior of the `dd-sword2` service is user specific, meaning that it depends on the user that is depositing data. A user can
be configured with a profile that determines this behavior. It is also possible to configure a default profile that is used for
users that do not have a profile of their own. The way a user account is authenticated is determined by its profile and the
default
profile together.

Authentication is explained in more detail below. For the other profile settings, see the comments in the [default configuration
file](https://github.com/DANS-KNAW/dd-sword2/blob/master/src/main/assembly/dist/cfg/config.yml){:target=_blank}.

Configuring authentication
--------------------------

The `dd-sword2` service can be configured to authenticate users in the following ways:

* Basic authentication (username/password) against a password hash specified in the user's profile. The hash must be calculated
  with the [BCrypt algorithm](https://en.wikipedia.org/wiki/Bcrypt){:target=_blank} by default. In the example below the user's password is
  simply "user001":

        userProfiles:
            users:
            - name: user001
              passwordHash: '$2a$10$yvmSYczU7z4KL6qmRCTgTeSvo7uurwPUbB9s/mTKzJrYM/sQKgF.y'
              collections:
               - collection1
              filepathMapping: true

  For compatibility with legacy passwords, set `unsaltedSha1: true` on an individual user to use a Base64-encoded unsalted
  SHA1 hash of the UTF-8 password instead. The flag defaults to `false`, retaining BCrypt authentication. Unsalted SHA1
  is weak and should only be used for existing legacy hashes; prefer BCrypt for new passwords. This example also uses
  the password "user001":

        userProfiles:
            users:
            - name: user001
              passwordHash: 'hN/rof975YOfV0wcZVXCrpU8ZlY='
              unsaltedSha1: true
              collections:
               - collection1
              filepathMapping: true

* Delegating authentication to a remote service, by forwarding specific headers to that service. This is used for users that are
  not in aforementioned list, or are in the list but without the `passwordHash` key. The remote service is configured in the
  default profile. In the example below the headers `Authorization` and `X-Dataverse-key` are forwarded to the remote service.

        userProfiles:
            default:
               passwordDelegate:
                  url: 'http://localhost:20340/'
                  forwardHeaders:
                  - Authorization
                  - X-Dataverse-key

               collections:
               - collection1
               filepathMapping: true

### Some example scenarios:

| Scenario                                                       | How to configure                                                                                                                                                                                                           |
|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Only users with a profile can deposit data.                    | Configure the users in <br/> `userProfiles.users` with a  <br/>`passwordHash` and leave out <br/> `userProfiles.default.passwordDelegate`<br/> or set it to `null`                                                         |
| All users can deposit data and share a single profile          | Configure the <br/>`userProfiles.default` section and<br/> include `passwordDelegate` to the system<br/> that authenticates the users; leave<br/> `userProfiles.users` null or empty                                       |
| All users can deposit data, some users need different settings | Configure the `userProfiles.default`<br/> section and include `passwordDelegate`<br/> to the system that authenticates <br/>the users; configure the other users in <br/>`userProfiles.users` with their own<br/> settings | 

Authentication process
----------------------
The `dd-sword2` service uses the following process to authenticate a user:

* If basic authentication credentials are found in the request:
    * If the username in the credential corresponds with a profile that has a `passwordHash`, validate the password againt the
      hash.
    * Otherwise:
        * If a default profile with a `passwordDelegate` is configured: delegate authentication to the remote service.
        * Return a `401 Unauthorized` response.
* If no basic authentication credentials are found in the request:
    * If a default profile with a `passwordDelegate` is configured: delegate authentication to the remote service.
    * Return a `401 Unauthorized` response.

Delegation protocol
-------------------

The `dd-sword2` service delegates authentication to a remote service by forwarding the configured headers to that service, if
found in the original request, by means of a `POST` request without a body. The remote service is expected to return a `200 OK`
with a body that contains the user's name in a simple json file, for example:

```json
{
  "userId": "user001"
}
```

If the credentials are not valid, the remote service is expected to return a `401 Unauthorized`.

An example implementation of such a remote service can be found in the [dd-dataverse-authenticator]{:target=_blank} project. This
is the service that is used in the DANS Data Stations.

[dd-dataverse-authenticator]: https://dans-knaw.github.io/dd-dataverse-authenticator/