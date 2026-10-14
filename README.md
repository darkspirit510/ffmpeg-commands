# ffmpeg-commands

A Kotlin tool that generates ffmpeg conversion commands for video files. The tool analyzes input files and creates
optimized transcoding commands with automatic language handling for audio and subtitles.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Usage

To use this tool, you need to have Java installed on your system. Run the tool using:

```bash
java -jar ffmpeg-commands.jar filename.mkv [options]
```

### Available Parameters

| Parameter                       | Description                                                                               |
|---------------------------------|-------------------------------------------------------------------------------------------|
| `alias`                         | Set custom alias for ffmpeg command                                                       |
| `additionalLanguages`           | Comma-separated list of additional languages to include (default: deu, ger, eng)          |
| `docker`                        | Create command to directly run in docker mode                                             |
| `unstarted`                     | When used with `-docker`, creates a container but does not start it (requires `-docker`)  |
| `dropSubtitles`                 | Drop all subtitles from output                                                            |
| `ignoreMissingSubtitleLanguage` | Ignore streams with missing subtitle language                                             |
| `setAudioLanguages`             | Comma-separated list of languages to assign to audio streams without language information |
| `maxInterleaveDelta`            | Override max interleaving delta in milliseconds (default: 0)                              |
| `twoPassTranscode`              | Generate two separate commands for two-pass transcoding (video first, then audio+merge)   |
| `deinterlace`                   | Deinterlace with ffmpeg's `nnedi` filter at double frame rate (e.g. 25i → 50p)            |

## Behavior

- When no parameters are given, only German and English streams are considered for audio tracks.
- Audio streams are always ordered: German, English, then all additional languages.
- When no AC3 stream is present, the tool adds an AC3 transcoded stream as the last audio track. This is necessary
  because some devices (like LG TVs) do not support DTS playback.

Note: The maxInterleaveDelta parameter controls how ffmpeg interleaves audio and video packets. The default value of 0
disables interleaving delta, which prevents "starting new cluster" spam but may cause muxer deadlocks with many audio
tracks. If you experience muxer deadlocks (ffmpeg dies without notice and resulting video can't be played), you may need
to set a positive value or use the twoPassTranscode parameter.
See [this link](https://www.reddit.com/r/ffmpeg/comments/efddfs/starting_new_cluster_due_to_timestamp/) for more
information.

Note: The twoPassTranscode parameter generates two commands for processing complex files in two passes. This can help
avoid muxer issues with files that have many audio tracks or high bitrates. Run the first command to transcode video,
then run the second command to process audio and merge with the video.

Note: The deinterlace parameter adds `-vf nnedi=...:field=af:deint=interlaced:nns=n128:qual=slow`, doubles the keyframe
interval to 480 and re-encodes AV1 sources instead of copying them. With `-twoPassTranscode` the filter is applied in
the video pass only. The `nnedi` filter needs a weights file: the tool extracts it to `~/.cache/ffmpeg-commands/` on
first use (and replaces a copy with the wrong size). In docker mode, that directory is mounted into the container as `/weights`.
The bundled `nnedi3_weights.bin` comes from [vapoursynth-nnedi3](https://github.com/dubhater/vapoursynth-nnedi3) and
is licensed under the GPLv2, see `src/main/resources/NNEDI3_WEIGHTS_LICENSE.txt`.

## Building

To build this project from source, you'll need Gradle installed. Clone the repository and run:

```bash
./gradlew build
```

This will create a jar file in the `build/libs` directory.

## Running

After building, you can run the tool with:

```bash
java -jar build/libs/ffmpeg-commands-1.0-SNAPSHOT.jar inputfile.mkv [options]
```

Or if you're using Docker mode:

```bash
java -jar build/libs/ffmpeg-commands-1.0-SNAPSHOT.jar inputfile.mkv -docker
```
