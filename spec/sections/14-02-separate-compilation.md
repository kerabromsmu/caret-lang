# Separate compilation

[Chapter index](../14-staging-compilation-and-compatibility.md) · [Language specification index](../../LANGUAGE.md)


<a id="separate-compilation-roots"></a>
## Separate compilation roots

<a id="roots-define-artifacts"></a>
### Roots define artifacts

Separate artifacts may be compiled from separate root source files.

For example:

```text
client.caret
server.caret
```

may each be passed independently to the compiler.

Conceptually:

```text
compile client.caret
    -> client artifact

compile server.caret
    -> server artifact
```

Each source file is the root of its own compilation reachability graph.

For each invocation, catalog discovery begins below the directory containing that root file. Two
roots in the same directory therefore normally discover the same project IDs; roots compiled from
different directory trees may have different visible project catalogs. Environment-supplied IDs,
including the normal standard library, are then combined with that root's discovered project IDs.

Caret does not require both targets to be declared inside one special project-level source construct.

A build system may invoke the Caret compiler once per root.

---

<a id="shared-source"></a>
### Shared source

Different roots may use the same source module:

```text
                  client-server.caret
                    /             \
                   /               \
          client.caret           server.caret
              |                       |
              v                       v
       client artifact          server artifact
```

The shared module may describe a larger logical system than either target individually needs.

Each compilation root may use compile-time computation to derive the portion relevant to that target.

This permits common definitions to remain in one source while producing separate deployment artifacts.

---

<a id="reachability"></a>
### Reachability

After compile-time execution is complete, the compiler performs normal program reachability analysis from the resulting runtime root.

Definitions reachable only from discarded compile-time structures do not belong to the runtime artifact.

For example, if a selected client rule requires:

```text
LoginMessage
LoginFormat
encode
validateName
```

those definitions remain reachable and are included as necessary.

A server-only rule and helpers used exclusively by that rule may be absent from the client artifact.

This is a semantic consequence of the resulting compiled program, not merely an optional size optimization.

The compiler must not require unreachable imported definitions to remain in an artifact solely because they were inspected during compile-time execution.

---

<a id="example-shared-clientserver-rules"></a>
## Example: shared client/server rules

<a id="shared-interaction-module"></a>
### Shared interaction module

A shared source file may define both sides of an interaction.

For example, `client-server.caret`:

```caret
clientServer = module

^client = context
^server = context

^interaction =
  ruleset
    sendLogin = rule [
      ^C = client
      ^T = loginRequestedTrigger
      ^E = sendLoginRequest
    ]

    authenticate = rule [
      ^C = server
      ^T = loginReceivedTrigger
      ^E = authenticateAndReply
    ]

    showLoginResult = rule [
      ^C = client
      ^T = loginResultReceivedTrigger
      ^E = showResult
    ]
```

The shared ruleset describes both client-side and server-side behavior.

The contexts:

```caret
client
server
```

are ordinary context values exported by the shared module.

They are not strings or compiler keywords.

---

<a id="client-compilation-root"></a>
### Client compilation root

`client.caret` may import the shared module at compile time:

```caret
# shared = import clientServer
```

and construct the runtime client ruleset by filtering the shared interaction:

```caret
clientRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.client
```

The resulting runtime program may then install those rules:

```caret
clientApp =
  ruleCycle
    init
      install clientRules
```

The binding:

```caret
shared
```

exists only during compilation.

Here `clientServer` is the shared file's stable ModuleId, `shared` is the client root's local
compile-time binding containing the imported module value, and `shared.client` is an exported
context value from that module. These are three different semantic entities. The import remains
valid if `client-server.caret` is moved to any other location below the directory used for this
compilation root's catalog discovery, provided its `clientServer = module` declaration remains.

The runtime artifact receives `clientRules` and whatever dependencies are reachable through them.

---

<a id="server-compilation-root"></a>
### Server compilation root

`server.caret` performs the corresponding selection:

```caret
```caret
<a id="shared-import-clientserver-4"></a>
```caret
# shared = import clientServer
```
```

serverRules =
  # shared.interaction filter $
    rule ->
      rule.context contains shared.server

serverApp =
  ruleCycle
    init
      install serverRules
```

Both compilation roots evaluate the same logical shared source.

Each creates a different runtime ruleset.

---

<a id="resulting-artifacts"></a>
### Resulting artifacts

Conceptually, the shared source contains:

```text
sendLogin
authenticate
showLoginResult
shared message definitions
shared formats
shared helper functions
client-only dependencies
server-only dependencies
```

The client compilation produces approximately:

```text
sendLogin
showLoginResult
required shared definitions
required client dependencies
client root
```

The server compilation produces approximately:

```text
authenticate
required shared definitions
required server dependencies
server root
```

A helper used by both sides may be included in both artifacts.

A helper used only by server rules need not appear in the client artifact.

The programmer specifies the semantic selection.

Normal compiler reachability determines the required dependency closure.

---
