# coach-chicken
 
A rep counter to make exercises more fun.

## Getting started (on Windows):

Install mediapipe using the instructions at: https://developers.google.com/mediapipe/framework/getting_started/install#installing_on_windows . 

Notes:
 * This is a long process to get to "Hello, World!" (10x)
 * Use Python 3.10 not 3.11 (not yet supported)
 * Visual Studio 2019 and C++ Build tools 2019
 * Choose option 2 for Bazelisk (not Bazel), only the `_VC` env var is needed iirc.

`pip install opencv-python`
`pip install mediapipe` (won't work on python 3.11, only 3.6-3.10)

Download a sample video to test with. e.g. I used Juice & Toya's 15-minute workout https://www.youtube.com/watch?v=xqVBoyKXbsA, downloaded and renamed it to 15-min.mp4, this is a good sample video.

`python coach.py` should then open the video and render frames with landmarks over it and a rep counter (current version is only squats).

Once this is working, then comment out the VideoCapture loading file (and the 2000 skip loop) and uncomment the VideoCapture(0) line. This should connect to your webcam and start counting your reps. BA-KAAWK!
