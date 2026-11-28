# Camera manual controls update

Added a Camera settings panel to the camera tool row with focus modes, white balance presets, exposure compensation, zoom, ISO, and shutter speed controls.

The camera request options are applied through CameraX Camera2 interop. Manual ISO/shutter are enabled when `Lock Focus + Manual Exposure` is selected. Actual supported ranges are clamped to Camera2 characteristics where available; zoom is clamped to the active camera's maximum zoom.

Device support varies. Camera2 controls are best-effort and this source update has not been built or tested on a physical device in this environment. Please verify preview and still capture behavior on the target phone.
