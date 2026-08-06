# Troubleshooting

## First Check

If the agent does not join or transcripts do not appear, run:

```bash
agora project doctor --deep
```

This checks credentials, project binding, feature enablement, network reachability, and quickstart env consistency.

## App Says Configuration Is Missing

Check `local.properties` for:

- `AGORA_APP_ID`
- `AGORA_APP_CERTIFICATE`
- `MURF_API_KEY`

## Agent Start Fails

Check:

- Conversational AI is enabled on the Agora project
- `AGORA_APP_ID` and `AGORA_APP_CERTIFICATE` belong to the same project
- the project supports RTC and RTM
- the App Certificate value is complete and correct
- the Murf API key is active and the selected `MURF_VOICE_ID` supports `MURF_LOCALE`

## RTM Login Or Transcript Flow Fails

Check:

- the token was generated for the same RTM user ID the app logs in with
- the channel name is the same for REST, RTC, and RTM
- the project has RTM available

## Agent Joins But Does Not Respond To Speech

Check:

- the generated requester RTC UID is passed in `remote_rtc_uids`
- `AGORA_AGENT_UID` does not collide with the local user UID
- the app joined RTC before you started speaking

## Metrics Do Not Appear

Check:

- the join payload includes `parameters.enable_metrics=true`
- RTM subscription succeeds for the same channel name
- your UI is wired to display metrics if you add a metrics panel

## Microphone Does Not Start

Check:

- Android microphone permission is granted
- the device is not blocking mic access at the system level
- the app joined the RTC channel successfully
