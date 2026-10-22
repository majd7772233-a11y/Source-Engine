# Third-Party License and Source-Provenance Audit

**Audit date:** 2026-10-22  
**Repository snapshot reviewed:** `majd7772233-a11y/Source-Engine`, `android-fixes`, commit `dadb5e18d5774535fe7d8e1b7c0511b3640c0296`  
**Release clearance: BLOCKED**  
**Review completed by: Pending**  
**Review date: Pending**  
**Review commit: Pending**  
**Evidence URL: Pending**

> This is a preliminary engineering inventory, not legal advice or a grant of rights. It deliberately does not infer permission to redistribute source or binaries from a repository being public, a “not for commercial use” About string, a successful build, or a license detected for one dependency. The release gate must stay blocked until provenance, required notices, redistribution rights, and review evidence are resolved by an accountable reviewer.

## Executive result

The current repository snapshot has no project-level `LICENSE`, `COPYING`, `NOTICE`, or `THIRD-PARTY-NOTICES` file in its tracked tree. The About string credits nillerusr/jusic/Valve and says “Not for commercial use”; that text does not identify the precise rights for the launcher code, every engine source file, or every shipped binary.

The build pins dependencies to immutable commit IDs, which improves reproducibility, but a source pin is not proof of a license or the right to redistribute its output. The current package contains 35 ARMv7 shared libraries. `scripts/workflow.sh` rebuilds five of them (`libtierhook.so`, `libmain.so`, `libRegal.so`, `libclient.so`, and `libserver.so`); the other 30 tracked binaries are carried forward from `libs/armeabi-v7a` without a committed per-binary provenance/license manifest. Several are engine/game-related libraries, and `libsteam_api.so` needs specific redistribution-rights confirmation. This audit has not established that these binaries are authorized to redistribute.

## Findings and evidence

| Component / evidence | What was verified at the reviewed snapshot | Status / required action |
|---|---|---|
| This Android application repository | The tracked Git tree at the commit above contains no file named as a project license, copyright notice, copying terms, or third-party notice. No single license is declared for this repository snapshot. | **Open.** The repository owner must document the intended license for original contributions without purporting to relicense upstream code. Preserve all applicable upstream terms. |
| Pinned Source SDK checkout | `scripts/dependencies.lock` pins [`nillerusr/srcsdk`](https://github.com/nillerusr/srcsdk/tree/413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d) at `413f5848d9a4534cdf8a4d10f5c1e7b1eaf3835d`. The repository's GitHub license metadata is unset. A recursive tree inspection at that exact revision found no root `LICENSE` or `thirdpartylegalnotices.txt`; it did contain `thirdparty/protobuf-2.3.0/COPYING.txt` and `thirdparty/protobuf-2.3.0/gtest/COPYING`. | **Critical open question.** Confirm source provenance and the license actually governing this fork, enumerate which third-party components are compiled/linked into each shipped library, and preserve their exact required notices. Do not assume Valve's official SDK license automatically covers a fork whose provenance has not been confirmed. |
| GL4ES source submodule | The pinned Source SDK tree references `gl4es/gl4es-git` at `6996a61a9efed4192ac76dedf96acf9110bb75d7`. The [LICENSE at that exact commit](https://github.com/nillerusr/gl4es/blob/6996a61a9efed4192ac76dedf96acf9110bb75d7/LICENSE) contains the MIT grant and requires retaining the copyright and permission notice. | **Action required.** Carry the applicable notice into the project's source/distribution notices and confirm the built `libRegal.so` lineage and any additional bundled GL4ES components. |
| Protobuf / Google Test notices in Source SDK | The pinned Source SDK contains `thirdparty/protobuf-2.3.0/COPYING.txt` and `thirdparty/protobuf-2.3.0/gtest/COPYING`. This filename inventory alone does not establish whether either component is present in the shipped APK. | **Open.** Trace static/dynamic linkage and the resulting native symbols; include notices where required. Do not copy notices without confirming the dependency/terms, but do not omit them if the distribution includes covered code. |
| Prebuilt native libraries | The checked-in `libs/armeabi-v7a` directory contains 35 `.so` files. The current native workflow overwrites/builds only five; 30 others are not reconstructed from a pinned source path by the workflow. The APK verifier validates ABI/dependencies, not copyright ownership. | **Critical open question.** Add a manifest mapping each binary's SHA-256 to origin, source commit/build recipe, rights/license and required notice, or replace it with a verified build output. Resolve `libsteam_api.so` redistribution permission independently. |
| SDL Java/native integration | `src/org/libsdl/app/` contains SDL-namespaced Java glue and the APK carries `libSDL2.so`, but the repository does not record the exact upstream SDL revision or a license notice alongside this integration. | **Open.** Identify the exact upstream version/fork and local patches; add the applicable SDL license/copyright text and verify the native binary's origin. |
| Build-time toolchain repositories | `scripts/dependencies.lock` pins `LostGamer/ndk-binaries` and `LostGamer/android-sdk` at fixed commits hosted on GitLab. These are build inputs and are not, by themselves, proof of app redistribution rights. Their provenance/license terms are not captured in this repository's release notices. | **Open.** Review each toolchain's license and distinguish tooling used only in CI from any code copied into the APK. |
| Official Valve guidance (reference only) | [Valve's official Source 1 SDK license](https://github.com/ValveSoftware/source-sdk-2013/blob/master/LICENSE) says its covered SDK/substantial distributions must be free and include the license and third-party notices. [Valve's Source Engine distribution guidance](https://partner.steamgames.com/doc/sdk/uploading/distributing_source_engine?language=english) says a mod based on leaked or stolen source code is not permitted. | **Not a conclusion about this fork.** The reviewed `nillerusr/srcsdk` commit has not been proven to be the same licensed distribution as Valve's official repository. Determine provenance and obtain applicable rights-holder advice/permission before release. |

### Binary inventory requiring provenance

The 30 tracked prebuilt `.so` files not rebuilt by `scripts/workflow.sh` at this snapshot are:

`libGameUI.so`, `libSDL2.so`, `libServerBrowser.so`, `libandroidwrapper.so`, `libclient_episodic.so`, `libclient_hl2.so`, `libclient_portal.so`, `libdatacache.so`, `libengine.so`, `libfilesystem_stdio.so`, `libfirst.so`, `libinputsystem.so`, `liblauncher.so`, `libmaterialsystem.so`, `libscenefilecache.so`, `libserver_episodic.so`, `libserver_hl2.so`, `libserver_portal.so`, `libshaderapidx9.so`, `libsoundemittersystem.so`, `libstdshader_dx9.so`, `libsteam_api.so`, `libstudiorender.so`, `libtier0.so`, `libtogl.so`, `libvgui2.so`, `libvguimatsurface.so`, `libvideo_services.so`, `libvphysics.so`, and `libvstdlib.so`.

These names are an inventory, not an assertion that every binary has the same license or origin.

## Release-blocking actions

Before a signed/release APK may be produced:

1. Confirm the authority and license for the pinned Source SDK fork and every submodule/source checkout actually used to build the APK; explicitly resolve any concern about unauthorized or leaked code.
2. Create a verified per-binary manifest with SHA-256, exact source origin/commit or rights-holder grant, license and notice requirements for all 35 packaged native libraries. Rebuild or remove binaries whose origin/rights cannot be established.
3. Record the exact SDL Java and native source revisions and include the required copyright/license texts. Review the embedded Steam API library's redistribution terms independently.
4. Add the correct project/upstream license and third-party notice texts to the repository and, where terms require, the APK/user-visible About or open-source notices page. Do not invent a permissive project license or replace upstream terms with the repository author's own.
5. Have an accountable maintainer/legal reviewer record the result, date, reviewed commit and evidence URL in the header above. If any component remains unclear, keep the gate blocked and resolve/remove/replace that component first.

## Release-gate behavior

`scripts/verify-release-license-gate.py` is intentionally fail-closed. The normal source/CI checks may still build and verify a debug APK internally for engineering work, but the workflow withholds uploading any new downloadable debug artifact while the audit is blocked or the review record is incomplete. A release-tag build or manual workflow dispatch with `sign_release=true` stops before fetching/building release dependencies under the same conditions. Passing synthetic tests proves only the guard's behavior; it does **not** establish that licensing has been cleared.

Previously uploaded debug artifacts or a green CI run are not license clearance and should not be described as approved public releases.
