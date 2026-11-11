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
| `twoPassTranscode`              | Always use two passes (ffmpeg, then mkvmerge), see [Two passes](#two-passes)              |
| `twoPassThreshold`              | RAM estimate in GB above which two passes are used (default: 8, e.g. `-twoPassThreshold=4.5`) |
| `deinterlace`                   | Deinterlace with ffmpeg's `nnedi` filter at double frame rate (e.g. 25i → 50p)            |
| `trimStart`                     | Cut the given number of seconds from the start of all streams (e.g. `-trimStart=28.5`)    |
| `reduceRAM`                     | Encode with less parallelism to lower the memory use; slower, same output                 |

## Behavior

- When no parameters are given, only German and English streams are considered for audio tracks.
- Audio streams are always ordered: German, English, then all additional languages.
- When no AC3 stream is present, the tool adds an AC3 transcoded stream as the last audio track. This is necessary
  because some devices (like LG TVs) do not support DTS playback.
- Every audio stream in the output gets a title made of its channel layout and codec, replacing whatever title the
  source had (e.g. `Surround 7.1 (TrueHD Atmos)`, `Stereo (AAC)`). See [Audio titles](#audio-titles).

## Audio titles

The title is `<layout> (<codec>)`, read from the stream line of `ffmpeg -i`:

| ffmpeg layout                                       | Layout       |
|-----------------------------------------------------|--------------|
| `mono`                                              | Mono         |
| `stereo`, `downmix`                                 | Stereo       |
| `2.1`                                               | Stereo 2.1   |
| `3.0`, `3.0(back)`, `3.1`, `4.0`, `quad`, `4.1`, …  | Surround 3.0, Surround 3.1, Surround 4.0, … (suffix such as `(side)` dropped) |
| `5.0`, `5.1`, `5.1(side)`, …                        | Surround 5.0, Surround 5.1 |
| `6.0`, `hexagonal`, `6.1`, …                        | Surround 6.0, Surround 6.1 |
| `7.0`, `7.1`, `7.1(wide)`, …                        | Surround 7.0, Surround 7.1 |
| `octagonal`                                         | Surround 8.0 |

| ffmpeg codec (profile)                               | Codec                         |
|------------------------------------------------------|-------------------------------|
| `ac3`, `eac3`, `truehd`                              | AC3, E-AC3, TrueHD            |
| `eac3`/`truehd` with a Dolby Atmos profile           | E-AC3 Atmos, TrueHD Atmos     |
| `dts (DTS)`, `dts (DTS-ES)`, `dts (DTS 96/24)`       | DTS                           |
| `dts (DTS-HD MA)`, `dts (DTS-HD HRA)`                | DTS-HD MA, DTS-HD HRA         |
| `dts (DTS-HD MA + DTS:X)`                            | DTS:X                         |
| `aac`, `flac`, `opus`, `vorbis`, `mp3`, `mp2`        | AAC, FLAC, Opus, Vorbis, MP3, MP2 |
| `pcm_*`                                              | PCM                           |

An unknown layout (e.g. `6 channels`) or codec on an audio stream that ends up in the output aborts with an error, so
the file can be inspected; streams dropped by the language filter are not checked. The added AC3 stream is titled by
its own layout: a 6.x, 7.x or 8.0 source is downmixed with `-ac 6` and titled `Surround 5.1 (AC3)`, as AC3 carries at
most 5.1 channels.

Note: The maxInterleaveDelta parameter controls how ffmpeg interleaves audio and video packets. The default value of 0
disables interleaving delta, which prevents "starting new cluster" spam but may cause muxer deadlocks with many audio
tracks. If you experience muxer deadlocks (ffmpeg dies without notice and resulting video can't be played), you may need
to set a positive value or use the twoPassTranscode parameter (see [Two passes](#two-passes)).
See [this link](https://www.reddit.com/r/ffmpeg/comments/efddfs/starting_new_cluster_due_to_timestamp/) for more
information.

Note: The deinterlace parameter adds `-vf nnedi=...:field=af:deint=interlaced:nns=n128:qual=slow`, doubles the keyframe
interval to 480 and re-encodes AV1 sources instead of copying them. With two passes the filter is applied in
the video pass only. The `nnedi` filter needs a weights file: the tool extracts it to `~/.cache/ffmpeg-commands/` on
first use (and replaces a copy with the wrong size). In docker mode, that directory is mounted into the container as `/weights`.
The bundled `nnedi3_weights.bin` comes from [vapoursynth-nnedi3](https://github.com/dubhater/vapoursynth-nnedi3) and
is licensed under the GPLv2, see `src/main/resources/NNEDI3_WEIGHTS_LICENSE.txt`.

Note: The trimStart parameter adds `-ss` to the source input and `-copypriorss 0` to the output, which keeps copied
audio and subtitles from starting at the keyframe before the cut. AV1 sources are rejected, because a
copied video stream can only be cut at a keyframe; with `-deinterlace` they are re-encoded and can be trimmed. A subtitle
still on screen at the cut is dropped; chapters are shifted to the new start. Subtitles are read through a second `-ss`
input of the same file: on a real file, subtitles sharing an input with audio made the AC3 encodes end right after the cut.

Note: The reduceRAM parameter adds `:lp=3` to `-svtav1-params` and `-threads 4` before the video input. SVT-AV1 picks its
level of parallelism from the host's CPU count (Docker CPU limits don't change it), up to level 6, which in CRF mode
buffers seven extra mini-GOPs of frames; levels 1 to 3 buffer none. The encode gets slower. The CRF output and the
version tag stay the same. With two passes only the ffmpeg pass changes. AV1 sources are rejected, since their
video is copied and not encoded.

Note: A PGS subtitle track whose first subtitle starts late makes ffmpeg warn `Could not find codec parameters ...
unspecified size`. The warning is harmless, as copied subtitles stay intact. When it shows up while the file is read,
the commands add `-analyzeduration 600M -probesize 5G` to every ffmpeg source input, which silences it for a first subtitle
within 10 minutes and 5 GB. ffmpeg stops probing once all streams are resolved and keeps what it read in RAM until those
packets are processed, so memory use peaks at the start of a run, by up to 5 GB per source input.

## Two passes

By default the tool prints a single ffmpeg command. That final ffmpeg mux breaks on sparse subtitle tracks (e.g. a
forced track with a few lines and gaps of 20 minutes): ffmpeg's scheduler always feeds the output stream that is
furthest behind, so it holds back everything else until the next subtitle line. With `-max_interleave_delta 0` the
muxer buffers all of that in RAM, up to an out-of-memory kill; with a positive value it writes the file badly
interleaved.

So the tool estimates that RAM and switches to two commands when the estimate exceeds 8 GB (`-twoPassThreshold`):

1. ffmpeg encodes the video and the AC3 tracks into `Output/movie_intermediate.mkv`, without chapters or global tags.
2. mkvmerge combines that file with the copied audio tracks, subtitles, chapters, attachments and global tags of the
   source into `Output/movie.mkv`. The track order is the same as with a single command. The AC3 tracks get their
   [audio title](#audio-titles) in step 1, the copied audio tracks through mkvmerge's `--track-name`.

Run them one after the other. The reason is printed above them as a shell comment:

```
# Two passes: subtitle stream #0:2 goes 3044 s without a line, a single-pass mux would buffer about 9.0 GB (threshold 8 GB)
```

The estimate is the longest gap of any selected subtitle track (before the first line, between lines, and after the
last line until the end of the file; with `-trimStart` from the cut) times the bitrate of everything else the muxer
holds back: the `BPS` tags of the copied audio tracks and of the source video, plus 640 kb/s per AC3 encode. The source
video bitrate overestimates the encoded video on purpose. 1 GB is 10⁹ bytes. The subtitle lines are found in the Cues
(the index) of the Matroska source, which the tool reads itself without reading the whole file; mkvmerge and ffmpeg
write a cue for every subtitle line. A selected subtitle track without any cue counts as one gap over the whole file.

The check is skipped with a single command and a `# Single pass, subtitle gaps not checked: ...` comment when the source
is not Matroska, has no subtitle cues, reports no duration, or lacks a `BPS` tag on a buffered stream. With
`-dropSubtitles` there is nothing to check. `-twoPassTranscode` forces two commands without a check and wins over
`-twoPassThreshold`; a high threshold keeps a single command.

With `-docker`, mkvmerge runs in `jlesage/mkvtoolnix`; otherwise the host's `mkvmerge` is used (`-alias` only changes
the ffmpeg command). mkvmerge has no `-n` and overwrites silently, so the mux command starts with
`[ ! -e Output/movie.mkv ] &&` and does nothing when the output exists; with `-unstarted` the guard is left out, since
it would run when the container is created. The intermediate file is not deleted. mkvmerge selects source tracks by
ffmpeg's stream index, so `-twoPassTranscode` only supports Matroska sources. `-trimStart` cannot be combined with two
passes yet (see issue #27): when the check asks for two passes, the tool stops with an error; drop the subtitles with
`-dropSubtitles` to trim anyway.

When the video is copied (AV1 source from Matroska):

- With no AC3 encode needed, a single mkvmerge command remuxes the source, with audio titles and languages set through
  `--track-name` and `--language`. No ffmpeg command and no check.
- With an AC3 encode, the single ffmpeg command stays unless two passes are detected or forced; then ffmpeg encodes
  only the AC3 tracks into `Output/movie_intermediate.mkv` and mkvmerge takes the video from the source.

AV1 sources that are not Matroska keep the single ffmpeg command, `-twoPassTranscode` is ignored for them.

## Version tag

The encoder settings leave no trace in the output file, so every command that encodes video writes the tool version
into the video stream of the final file:

```
-metadata:s:v:0 FFMPEG_COMMANDS_VERSION=1.0
```

With two passes the tag is set in the first (ffmpeg) command, and mkvmerge keeps it in the final file. When the
video is copied (AV1 source), the tool adds no tag, since nothing was encoded. ffmpeg and mkvmerge still copy an existing tag from
the source, so a re-muxed file keeps the version it was encoded with.

`mediainfo --Output=JSON` shows the tag in the video track's `extra` object:

```json
{ "@type": "Video", "extra": { "FFMPEG_COMMANDS_VERSION": "1.0" } }
```

The version (`MAJOR.MINOR`) describes the output files:

| Change | Meaning for encoded files                                                                          |
|--------|----------------------------------------------------------------------------------------------------|
| Major  | The video encoding settings changed. Files with an older major version are worth re-encoding.     |
| Minor  | Something else in the output changed (audio, subtitles, metadata, ...). No re-encode needed.       |

Changes that leave the output file unchanged keep the version. Files without the tag were encoded before versioning
was introduced, with unknown settings; `1.0` stands for 10-bit SVT-AV1 with `tune=0`, `enable-qm=1` and
`enable-variance-boost=1`.

## Building

To build this project from source, you'll need Gradle installed. Clone the repository and run:

```bash
./gradlew build
```

This will create a jar file in the `build/libs` directory.

## Running

After building, you can run the tool with:

```bash
java -jar build/libs/ffmpeg-commands-1.0.jar inputfile.mkv [options]
```

Or if you're using Docker mode:

```bash
java -jar build/libs/ffmpeg-commands-1.0.jar inputfile.mkv -docker
```
