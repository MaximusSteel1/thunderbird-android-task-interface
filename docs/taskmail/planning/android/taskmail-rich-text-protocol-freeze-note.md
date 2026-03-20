# TaskMail Rich Text Protocol Freeze Note

## Status

- Date: 2026-03-19
- Scope: Android-side consumer freeze note for the current rich-text body slice
- Layer: implementation-reference freeze note

Current authority remains:

- `docs/TASKMAIL-MAIL-RULES.md`
- `docs/TASKMAIL-ANDROID-CURRENT-STATUS.md`
- PC-side current protocol docs in `E:\projects\mail_based_task_manager\docs/current/`

This note does not replace those sources.
It freezes the protocol-facing assumptions that the Android implementation should now treat as stable unless both
repositories explicitly agree to change them.

## Freeze Now

### 1. HTML Is A Reading Projection, Not A Fact Source

- `text/plain` remains the parsing and reply truth source.
- HTML is consumed only as a display projection for Task Detail.
- Android must not depend on HTML to extract state, questions, run results, or routing facts.

### 2. The Consumable HTML Unit Is `article.task-mail`

- the Android consumer contract is the subtree under `article.task-mail`
- outer mail-safe wrapper markup is transport noise, not the semantic body contract
- if `article.task-mail` is missing, Android falls back to plain-text rendering

### 3. Inline Image Matching Uses `contentId`, Not Guesswork

- inline HTML images are matched to attachments by `contentId`
- normalization may remove surrounding angle brackets and trim whitespace
- Android should not fall back to filename, caption, attachment order, or other heuristics
- if a `cid:` image cannot be resolved through attachment metadata, Android must skip inline rendering and keep the
  attachment visible through the normal attachment path

### 4. Static SVG Uses The Existing Inline-Image Path

- formula-like output arrives as static attached `image/svg+xml`
- Android treats it as the same inline-image category as other attached previewable images
- Android does not introduce `MathInline`, `MathBlock`, or a formula-specific protocol field
- if inline SVG cannot be rendered, Android falls back to plain text and/or normal attachment display

### 5. Unsupported HTML Is Allowed To Degrade

- the PC-side allowed HTML subset is broader than Android v1 rendering obligations
- unsupported blocks may degrade to plain text or an explicit unsupported block
- lack of full fidelity for an allowed tag does not by itself mean protocol failure

## Freeze Safety Constraints

These should be treated as protocol-facing constraints, not optional implementation detail:

- inline-previewed SVG must be static
- no script, animation, iframe, form, or active content
- no external resource fetches for inline SVG
- meaningful plain-text fallback remains required
- meaningful HTML `alt` text remains required for inline image/SVG preview
- externally delivered files are not inline body images for the current mail

## Deliberately Not Frozen In This Slice

The following are not part of the current rich-text body freeze:

- emitted subject-shape changes
- `TaskRunPacket` internal projection shape
- same-workspace targeted-command UI support
- full table/layout fidelity beyond safe rich-text degradation
- any Markdown-first renderer convergence

## Failure Policy

If a future change would alter any frozen item above, do not silently adapt Android code first.

Instead:

1. update the PC-side current protocol docs
2. update this Android freeze note or the relevant Android authority doc
3. only then change the Android implementation
