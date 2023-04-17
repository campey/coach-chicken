# coach-chicken
 
A rep counter to make exercises more fun.

## Getting started (on Windows):

 * Install python 3.10 from https://www.python.org/downloads/release/python-31010/
 * `pip install opencv-python`
 * `pip install mediapipe` (won't work on python 3.11, only 3.6-3.10)
 * `python coach.py`, wait a few seconds, and the camera frames should start displaying with reps and body landmarks.

### Option for pre-recorded test
 * Download a sample video to test with. e.g. I used Juice & Toya's 15-minute workout https://www.youtube.com/watch?v=xqVBoyKXbsA, downloaded and renamed it to 15-min.mp4, this is a good sample video.
 * Change the code to use the video file not capture.
   * comment the VideoCapture(0) line
   * uncomment the VideoCapture loading file (and the 2000 skip loop)
 * `python coach.py` should then open the video and render frames with landmarks over it and a rep counter (current version is only squats).
