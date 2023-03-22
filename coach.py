import cv2
import mediapipe as mp

mpDraw = mp.solutions.drawing_utils
mpPose = mp.solutions.pose
pose = mpPose.Pose()

cap = cv2.VideoCapture("15-min.mp4")

for i in range(2000):
    cap.read()

# cap = cv2.VideoCapture(0)

RIGHT_WRIST = 16
RIGHT_HIP = 24
RIGHT_KNEE = 26

LEFT_WRIST = 15
LEFT_HIP = 23
LEFT_KNEE = 25

squat_landmarks = [RIGHT_WRIST, RIGHT_HIP, RIGHT_KNEE, LEFT_WRIST, LEFT_HIP, LEFT_KNEE]
squatting = False
squat_count = 0

#while True:
for i in range(1000):
    success, img = cap.read()
    imgRGB = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

    results = pose.process(imgRGB)
    # print(results.pose_landmarks)
    # print("------------------------")
    
    h,w,c = img.shape
    points = {}
    if results.pose_landmarks:
        # we have a human in frame
        mpDraw.draw_landmarks(img, results.pose_landmarks, mpPose.POSE_CONNECTIONS)
        for id, lm in enumerate(results.pose_landmarks.landmark):
            cx, cy = int(lm.x*w), int(lm.y*h)
            # print(id, cx, cy) #lm,
            points[id] = (cx, cy)

        # print(f"Right w:{points[RIGHT_WRIST]}, h: {points[RIGHT_HIP]}, k:{points[RIGHT_KNEE]}")
        # print(f"Left  w:{points[LEFT_WRIST]}, h: {points[LEFT_HIP]}, k:{points[LEFT_KNEE]}")
        # print("---------")
        circle_color = (0,0,0)
        if points[RIGHT_WRIST][1] > points[RIGHT_KNEE][1] and \
            points[LEFT_WRIST][1] > points[LEFT_KNEE][1]:
            circle_color = (0,200,255)
            if not squatting:
                print("SQUAT DOWN")
                squatting = True

        elif points[RIGHT_WRIST][1] < points[RIGHT_KNEE][1] and \
            points[LEFT_WRIST][1] < points[LEFT_KNEE][1]:
            circle_color = (0,255,255)
            if squatting:
                print("SQUAT UP")
                squatting = False
                squat_count = squat_count + 1
                print(f"SQUAT_REP {squat_count}")


        for landmark in squat_landmarks:
            cv2.circle(img, points[landmark], 15, circle_color, cv2.FILLED)

        cv2.putText(img, str(squat_count), (100, 150), cv2.FONT_HERSHEY_PLAIN, 12, circle_color, 12)


    cv2.imshow("img", img)
    cv2.waitKey(1)





