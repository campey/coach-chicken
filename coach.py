import cv2
import mediapipe as mp

mpDraw = mp.solutions.drawing_utils
mpPose = mp.solutions.pose
pose = mpPose.Pose()

# cap = cv2.VideoCapture("15-min.mp4")

# for i in range(2000):
#     cap.read()

cap = cv2.VideoCapture(0)

RIGHT_SHOULDER = 12
RIGHT_ELBOW = 14
RIGHT_WRIST = 16
RIGHT_HIP = 24
RIGHT_KNEE = 26

LEFT_SHOULDER = 11
LEFT_ELBOW = 13
LEFT_WRIST = 15
LEFT_HIP = 23
LEFT_KNEE = 25

squat_landmarks = [RIGHT_WRIST, RIGHT_HIP, RIGHT_KNEE, LEFT_WRIST, LEFT_HIP, LEFT_KNEE]
squatting = False
squat_count = 0

press_landmarks = [RIGHT_ELBOW, RIGHT_SHOULDER, LEFT_ELBOW, LEFT_SHOULDER]
pressing = False
press_count = 0

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
            # print(id, lm) #cx, cy) #lm,
            points[id] = (cx, cy)

        # print(f"Right w:{points[RIGHT_WRIST]}, h: {points[RIGHT_HIP]}, k:{points[RIGHT_KNEE]}")
        # print(f"Left  w:{points[LEFT_WRIST]}, h: {points[LEFT_HIP]}, k:{points[LEFT_KNEE]}")
        # print("---------")

        squat_color = (0,0,0)
        
        if points[RIGHT_WRIST][1] > points[RIGHT_KNEE][1] and \
            points[LEFT_WRIST][1] > points[LEFT_KNEE][1]:
            squat_color = (0,200,255)
            if not squatting:
                print("SQUAT DOWN")
                squatting = True

        elif points[RIGHT_WRIST][1] < points[RIGHT_KNEE][1] and \
            points[LEFT_WRIST][1] < points[LEFT_KNEE][1]:
            squat_color = (0,255,255)
            if squatting:
                print("SQUAT UP")
                squatting = False
                squat_count = squat_count + 1
                print(f"SQUAT_REP {squat_count}")


        press_color = (0,0,0)
        
        if points[RIGHT_ELBOW][1] < points[RIGHT_SHOULDER][1] and \
            points[LEFT_ELBOW][1] < points[LEFT_SHOULDER][1]:
            press_color = (255,200,0)
            if not pressing:
                print("PRESS UP")
                pressing = True

        elif points[RIGHT_ELBOW][1] > points[RIGHT_SHOULDER][1] and \
            points[LEFT_ELBOW][1] > points[LEFT_SHOULDER][1]:
            press_color = (255,255,0)
            if pressing:
                print("PRESS DOWN")
                pressing = False
                press_count = press_count + 1
                print(f"PRESS_REP {press_count}")


        for landmark in squat_landmarks:
            cv2.circle(img, points[landmark], 15, squat_color, cv2.FILLED)

        for landmark in press_landmarks:
            cv2.circle(img, points[landmark], 15, press_color, cv2.FILLED)

        cv2.putText(img, "Squat: " + str(squat_count), (50, 75), cv2.FONT_HERSHEY_PLAIN, 2, squat_color, 2)
        cv2.putText(img, "Press: " + str(press_count), (250, 75), cv2.FONT_HERSHEY_PLAIN, 2, press_color, 2)


    cv2.imshow("img", img)
    cv2.waitKey(1)





