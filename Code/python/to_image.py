import cv2
import os

# 定义视频文件夹路径和图像保存路径
video_folder = 'vedio'
image_base_folder = 'image'

# 获取vedio文件夹下的所有视频文件
video_files = [f for f in os.listdir(video_folder) if f.endswith('.avi')]

# 遍历每个视频文件
for i, video_file in enumerate(video_files, start=1):
    # 创建对应的图像保存文件夹
    image_folder = os.path.join(image_base_folder, f'vedio{i}')
    os.makedirs(image_folder, exist_ok=True)

    # 打开视频文件
    video_path = os.path.join(video_folder, video_file)
    cap = cv2.VideoCapture(video_path)

    frame_count = 0
    while True:
        ret, frame = cap.read()
        if not ret:
            break

        # 每5帧抽取一张图片
        if frame_count % 5 == 0:
            image_path = os.path.join(image_folder, f'vedio{i}_image{frame_count:04d}.jpg')
            cv2.imwrite(image_path, frame)

        frame_count += 1

    # 释放视频捕获对象
    cap.release()

print("处理完成！")