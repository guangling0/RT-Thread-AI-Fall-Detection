from libs.PipeLine import PipeLine
from libs.AIBase import AIBase
from libs.AI2D import Ai2d
from libs.Utils import *
import os,sys,ujson,gc,math
from media.media import *
import nncase_runtime as nn
import ulab.numpy as np
import image
import aicube
import time
from machine import FPIOA
from machine import UART
import time
import json
fpioa = FPIOA()
fpioa.set_function(11,FPIOA.UART2_TXD)
fpioa.set_function(12,FPIOA.UART2_RXD)
uart = UART(UART.UART2,baudrate=921600,bits=UART.EIGHTBITS, parity=UART.PARITY_NONE, stop=UART.STOPBITS_ONE)
uart.write("AT+RST\r\n")
time.sleep(2)
uart.write('AT+MQTTUSERCFG=0,1,"826601","5A6KBU6yw5","version=2018-10-31&res=products%2F5A6KBU6yw5%2Fdevices%2F826601&et=1908174207&method=md5&sign=A8jHCnqoPUC85WRkSaJDzQ%3D%3D",0,0,""\r\n')
time.sleep(2)
uart.write('AT+MQTTCONN=0,"mqtts.heclouds.com",1883,1\r\n')
time.sleep(2)

# 自定义跌倒检测类，继承自AIBase基类
class FallDetectionApp(AIBase):
    def __init__(self, kmodel_path, model_input_size, labels, anchors, confidence_threshold=0.2, nms_threshold=0.5, nms_option=False, strides=[8,16,32], rgb888p_size=[224,224], display_size=[1920,1080], debug_mode=0):
        super().__init__(kmodel_path, model_input_size, rgb888p_size, debug_mode)  # 调用基类的构造函数
        self.kmodel_path = kmodel_path                      # 模型文件路径
        self.model_input_size = model_input_size            # 模型输入分辨率
        self.labels = labels                                # 分类标签
        self.anchors = anchors                              # 锚点数据，用于跌倒检测
        self.strides = strides                              # 步长设置
        self.confidence_threshold = confidence_threshold    # 置信度阈值
        self.nms_threshold = nms_threshold                  # NMS（非极大值抑制）阈值
        self.nms_option = nms_option                        # NMS选项
        self.rgb888p_size = [ALIGN_UP(rgb888p_size[0], 16), rgb888p_size[1]]  # sensor给到AI的图像分辨率，并对宽度进行16的对齐
        self.display_size = [ALIGN_UP(display_size[0], 16), display_size[1]]  # 显示分辨率，并对宽度进行16的对齐
        self.debug_mode = debug_mode                                          # 是否开启调试模式
        self.color = [(255,0, 0, 255), (255,0, 255, 0), (255,255,0, 0), (255,255,0, 255)]  # 用于绘制不同类别的颜色
        # Ai2d实例，用于实现模型预处理
        self.ai2d = Ai2d(debug_mode)
        # 设置Ai2d的输入输出格式和类型
        self.ai2d.set_ai2d_dtype(nn.ai2d_format.NCHW_FMT, nn.ai2d_format.NCHW_FMT, np.uint8, np.uint8)

    # 配置预处理操作，这里使用了pad和resize，Ai2d支持crop/shift/pad/resize/affine，具体代码请打开/sdcard/app/libs/AI2D.py查看
    def config_preprocess(self, input_image_size=None):
        with ScopedTiming("set preprocess config", self.debug_mode > 0):                    # 计时器，如果debug_mode大于0则开启
            ai2d_input_size = input_image_size if input_image_size else self.rgb888p_size   # 初始化ai2d预处理配置，默认为sensor给到AI的尺寸，可以通过设置input_image_size自行修改输入尺寸
            top, bottom, left, right,_ = center_pad_param(self.rgb888p_size,self.model_input_size)
            self.ai2d.pad([0, 0, 0, 0, top, bottom, left, right], 0, [0,0,0])               # 填充边缘
            self.ai2d.resize(nn.interp_method.tf_bilinear, nn.interp_mode.half_pixel)       # 缩放图像
            self.ai2d.build([1,3,ai2d_input_size[1],ai2d_input_size[0]],[1,3,self.model_input_size[1],self.model_input_size[0]])  # 构建预处理流程

    # 自定义当前任务的后处理，results是模型输出array的列表，这里使用了aicube库的anchorbasedet_post_process接口
    def postprocess(self, results):
        with ScopedTiming("postprocess", self.debug_mode > 0):
            dets = aicube.anchorbasedet_post_process(results[0], results[1], results[2], self.model_input_size, self.rgb888p_size, self.strides, len(self.labels), self.confidence_threshold, self.nms_threshold, self.anchors, self.nms_option)
            return dets

    # 绘制检测结果到画面上
    def draw_result(self, pl, dets):
        with ScopedTiming("display_draw", self.debug_mode > 0):
            if dets:
                pl.osd_img.clear()  # 清除OSD图像
                for det_box in dets:
                    # 计算显示分辨率下的坐标
                    x1, y1, x2, y2 = det_box[2], det_box[3], det_box[4], det_box[5]
                    w = (x2 - x1) * self.display_size[0] // self.rgb888p_size[0]
                    h = (y2 - y1) * self.display_size[1] // self.rgb888p_size[1]
                    x1 = int(x1 * self.display_size[0] // self.rgb888p_size[0])
                    y1 = int(y1 * self.display_size[1] // self.rgb888p_size[1])
                    x2 = int(x2 * self.display_size[0] // self.rgb888p_size[0])
                    y2 = int(y2 * self.display_size[1] // self.rgb888p_size[1])
                    # 绘制矩形框和类别标签
                    pl.osd_img.draw_rectangle(x1, y1, int(w), int(h), color=self.color[det_box[0]], thickness=2)
                    pl.osd_img.draw_string_advanced(x1, y1-50, 32," " + self.labels[det_box[0]] + " " + str(round(det_box[1],2)), color=self.color[det_box[0]])
            else:
                pl.osd_img.clear()

# 自定义图像分类类，用于区分"fall in ground"和"lay in bed"
class FallClassificationApp(AIBase):
    def __init__(self, kmodel_path, model_input_size, labels, confidence_threshold=0.5, rgb888p_size=[224,224], display_size=[1920,1080], debug_mode=0):
        super().__init__(kmodel_path, model_input_size, rgb888p_size, debug_mode)  # 调用基类的构造函数
        self.kmodel_path = kmodel_path                      # 模型文件路径
        self.model_input_size = model_input_size            # 模型输入分辨率
        self.labels = labels                                # 分类标签
        self.confidence_threshold = confidence_threshold    # 置信度阈值
        self.rgb888p_size = [ALIGN_UP(rgb888p_size[0], 16), rgb888p_size[1]]  # sensor给到AI的图像分辨率，并对宽度进行16的对齐
        self.display_size = [ALIGN_UP(display_size[0], 16), display_size[1]]  # 显示分辨率，并对宽度进行16的对齐
        self.debug_mode = debug_mode                        # 是否开启调试模式
        self.color = [(255,0, 0, 255), (255,255,0, 0)]    # 用于绘制不同类别的颜色

        # Ai2d实例，用于实现模型预处理
        self.ai2d = Ai2d(debug_mode)
        # 设置Ai2d的输入输出格式和类型
        self.ai2d.set_ai2d_dtype(nn.ai2d_format.NCHW_FMT, nn.ai2d_format.NCHW_FMT, np.uint8, np.uint8)

    # 配置预处理操作，这里使用了pad和resize
    def config_preprocess(self, input_image_size=None):
        with ScopedTiming("set preprocess config", self.debug_mode > 0):
            ai2d_input_size = input_image_size if input_image_size else self.rgb888p_size
            top, bottom, left, right,_ = center_pad_param(self.rgb888p_size, self.model_input_size)
            self.ai2d.pad([0, 0, 0, 0, top, bottom, left, right], 0, [0,0,0])  # 填充边缘
            self.ai2d.resize(nn.interp_method.tf_bilinear, nn.interp_mode.half_pixel)  # 缩放图像
            self.ai2d.build([1,3,ai2d_input_size[1],ai2d_input_size[0]],
                          [1,3,self.model_input_size[1],self.model_input_size[0]])  # 构建预处理流程

    # 自定义当前任务的后处理，results是模型输出array的列表
    def postprocess(self, results):
        with ScopedTiming("postprocess", self.debug_mode > 0):
            # 获取模型输出
            output = results[0]

            # 转换为概率分布（使用softmax）
            exp_values = np.exp(output - np.max(output))
            probabilities = exp_values / np.sum(exp_values)

            # 获取最大概率和对应的类别索引
            max_prob = np.max(probabilities)
            class_idx = np.argmax(probabilities)

            # 如果概率超过阈值，则返回分类结果
            if max_prob >= self.confidence_threshold:
                return [(class_idx, max_prob)]
            else:
                return []

    # 绘制分类结果到画面上
    def draw_result(self, pl, results):
        with ScopedTiming("display_draw", self.debug_mode > 0):
            pl.osd_img.clear()  # 清除OSD图像

            if results:
                class_idx, confidence = results[0]
                label = self.labels[class_idx]

                # 在画面中央绘制分类结果
                text = f"{label}: {confidence:.2f}"
                text_x = (self.display_size[0] - len(text) * 32) // 2  # 居中显示
                text_y = self.display_size[1] // 2 - 16

                # 绘制背景矩形和分类结果文本
                pl.osd_img.draw_rectangle(
                    text_x - 10, text_y - 10,
                    len(text) * 32 + 20, 50,
                    color=(0, 0, 0, 180),  # 半透明黑色背景
                    thickness=-1  # 填充矩形
                )
                pl.osd_img.draw_string_advanced(
                    text_x, text_y, 32, text,
                    color=self.color[class_idx]
                )

# 添加帧率计数器
#class FPSCounter:
#    def __init__(self):
#        self.frames = 0
#        self.start_time = time.time()
#        self.fps = 0

#    def update(self):
#        self.frames += 1
#        current_time = time.time()
#        elapsed = current_time - self.start_time

#        if elapsed > 1.0:
#            self.fps = self.frames / elapsed
#            self.frames = 0
#            self.start_time = current_time

#        return self.fps

#    def get_fps(self):
#        return self.fps

if __name__ == "__main__":
    # 添加显示模式，默认hdmi，可选hdmi/lcd/lt9611/st7701/hx8399,其中hdmi默认置为lt9611，分辨率1920*1080；lcd默认置为st7701，分辨率800*480
    display_mode="lcd"
    # k230保持不变，k230d可调整为[640,360]
    rgb888p_size = [1280, 720]
    # 设置模型路径和其他参数
    kmodel_path = "/sdcard/examples/kmodel/yolov5n-falldown.kmodel"
    confidence_threshold = 0.3
    nms_threshold = 0.45
    labels = ["Fall","NoFall"]  # 模型输出类别名称
    anchors = [10, 13, 16, 30, 33, 23, 30, 61, 62, 45, 59, 119, 116, 90, 156, 198, 373, 326]  # anchor设置

    # 设置图像分类模型参数
    classification_model = "/sdcard/mp_deployment_source/can2_10.0l_20250702222143.kmodel"  # 假设的分类模型路径
    classification_labels = ["fall in ground", "lay in bed"]
    classification_threshold = 0.5

    # 初始化PipeLine，用于图像处理流程
    pl = PipeLine(rgb888p_size=rgb888p_size, display_mode=display_mode)
    pl.create()
    display_size=pl.get_display_size()

    # 初始化自定义跌倒检测实例
    fall_det = FallDetectionApp(kmodel_path, model_input_size=[640, 640], labels=labels, anchors=anchors, confidence_threshold=confidence_threshold, nms_threshold=nms_threshold, nms_option=False, strides=[8,16,32], rgb888p_size=rgb888p_size, display_size=display_size, debug_mode=1)
    fall_det.config_preprocess()

    # 初始化图像分类实例
    classifier = FallClassificationApp(
        classification_model,
        model_input_size=[224, 224],  # 假设分类模型输入为224x224
        labels=classification_labels,
        confidence_threshold=classification_threshold,
        rgb888p_size=rgb888p_size,
        display_size=display_size,
        debug_mode=1
    )
    classifier.config_preprocess()

    # 初始化帧率计数器
#    fps_counter = FPSCounter()

    # 添加性能监控
    frame_count = 0
    last_report_time = time.time()
    report_interval = 10  # 每10秒报告一次性能
    fall_first = 0
    # 主循环
    try:
        while True:
            frame_start_time = time.time()

            # 获取当前帧数据
            img = pl.get_frame()

            # 执行跌倒检测
            fall_results = fall_det.run(img)
            fall_det.draw_result(pl, fall_results)
            print(fall_results)
            if(len(fall_results) != 0):
                for fall_result in fall_results:
                    if(fall_result[0]==0 && fall_result[1] >= 0.2):
                        class_results = classifier.run(img)
                        print(class_results)
                        if (class_results[0][0] == 0 && class_results[0][1] > 0.5):
                            uart.write('AT+MQTTPUB=0,"$sys/5A6KBU6yw5/826601/dp/post/json","{\\"id\\":123\,\\"dp\\":{\\"fall\\":[{\\"v\\":1}]}}",0,0\r\n')
                            print('检测到摔倒')
                            time.sleep(0.2）
                        else:
                            uart.write('AT+MQTTPUB=0,"$sys/5A6KBU6yw5/826601/dp/post/json","{\\"id\\":123\,\\"dp\\":{\\"fall\\":[{\\"v\\":0}]}}",0,0\r\n')
                            time.sleep(0.2)
                    else:
                        uart.write('AT+MQTTPUB=0,"$sys/5A6KBU6yw5/826601/dp/post/json","{\\"id\\":123\,\\"dp\\":{\\"fall\\":[{\\"v\\":0}]}}",0,0\r\n')
                        time.sleep(0.2)
            else:
                uart.write('AT+MQTTPUB=0,"$sys/5A6KBU6yw5/826601/dp/post/json","{\\"id\\":123\,\\"dp\\":{\\"fall\\":[{\\"v\\":0}]}}",0,0\r\n')
                time.sleep(0.2)

            # 显示刷新
            pl.show_image()

            # 垃圾回收
            gc.collect()

            # 性能监控
            frame_count += 1
            current_time = time.time()
            if current_time - last_report_time >= report_interval:
                elapsed = current_time - last_report_time
                avg_fps = frame_count / elapsed
                print(f"Performance report: {frame_count} frames in {elapsed:.2f} seconds, avg FPS: {avg_fps:.2f}")
                frame_count = 0
                last_report_time = current_time

    except KeyboardInterrupt:
        print("程序被用户中断")
    finally:
        # 确保资源被正确释放
        print("正在清理资源...")
        fall_det.deinit()
        classifier.deinit()
        pl.destroy()
        print("资源清理完成，程序退出")
