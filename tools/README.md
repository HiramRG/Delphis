# Tools

* `compose_menu_music.py`: generates the built-in menu music (`app/src/main/res/raw/menu_music.ogg`).
  Needs Python 3 with numpy and scipy, plus ffmpeg: `python3 compose_menu_music.py menu.wav && ffmpeg -i menu.wav -c:a libvorbis -q:a 3 menu_music.ogg`.
* `screenshots/`: HTML sources of the illustrations in the README. Render each page with headless Chrome at 2x
  (top screen 700x393, bottom screen 451x393), then run `compose.py` to place them in a Thor frame.
