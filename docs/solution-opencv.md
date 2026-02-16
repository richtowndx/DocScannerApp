# 方案B: OpenCV 自定义文档扫描方案

## 概述

基于OpenCV实现的完全自定义文档扫描方案，提供最大的灵活性和控制力。适合需要深度定制扫描流程的应用。

## 核心技术栈

```
Kotlin + OpenCV4Android + 自定义图像处理算法
```

## 依赖配置

```kotlin
// build.gradle.kts (app level)
implementation("com.quickbirdstudios:opencv:4.5.3.0")
// 或者使用官方OpenCV SDK
// implementation(files("libs/opencv-android-sdk.jar"))
```

## 功能实现详解

### 1. 边框识别算法

边框识别是扫描功能的核心第一步，目的是自动定位文档的物理边界。

#### 技术流程

```kotlin
suspend fun detectDocumentEdges(bitmap: Bitmap): List<PointF>? {
    val mat = Mat()
    Utils.bitmapToMat(bitmap, mat)

    // Step 1: 灰度化
    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

    // Step 2: 高斯模糊去噪
    val blurred = Mat()
    Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

    // Step 3: Canny边缘检测
    val edges = Mat()
    Imgproc.Canny(blurred, edges, 50.0, 150.0)

    // Step 4: 膨胀操作，连接断开的边缘
    val dilated = Mat()
    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
    Imgproc.dilate(edges, dilated, kernel)

    // Step 5: 查找轮廓
    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(dilated, contours, hierarchy,
        Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

    // Step 6: 找到最大的四边形轮廓
    val documentCorners = findLargestQuadrilateral(contours, mat.size())

    return documentCorners?.map { point ->
        PointF(point.x.toFloat(), point.y.toFloat())
    }
}
```

#### 关键参数说明

| 参数 | 值 | 说明 |
|------|-----|------|
| GaussianBlur核大小 | 5x5 | 去噪强度 |
| Canny低阈值 | 50 | 边缘检测下限 |
| Canny高阈值 | 150 | 边缘检测上限 |
| 轮廓近似精度 | 2%周长 | 多边形拟合精度 |
| 最小面积比例 | 5%图像面积 | 过滤小轮廓 |

### 2. 透视校正算法

将倾斜的文档转换为正矩形，消除拍摄角度导致的变形。

```kotlin
suspend fun applyPerspectiveCorrection(
    bitmap: Bitmap,
    corners: List<PointF>
): Bitmap {
    val srcMat = Mat()
    Utils.bitmapToMat(bitmap, srcMat)

    // 源点（检测到的四个角，按顺序：左上、右上、右下、左下）
    val srcPoints = MatOfPoint2f(
        Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
        Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
        Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
        Point(corners[3].x.toDouble(), corners[3].y.toDouble())
    )

    // 计算目标矩形的宽高
    val width = calculateMaxWidth(corners)
    val height = calculateMaxHeight(corners)

    // 目标点（正矩形的四个角）
    val dstPoints = MatOfPoint2f(
        Point(0.0, 0.0),
        Point(width.toDouble(), 0.0),
        Point(width.toDouble(), height.toDouble()),
        Point(0.0, height.toDouble())
    )

    // 计算透视变换矩阵
    val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

    // 应用透视变换
    val dstMat = Mat()
    Imgproc.warpPerspective(srcMat, dstMat, perspectiveMatrix,
        Size(width.toDouble(), height.toDouble()))

    // 转换为Bitmap
    val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    Utils.matToBitmap(dstMat, resultBitmap)

    return resultBitmap
}
```

### 3. 图像增强算法

#### 灰度化处理

```kotlin
fun applyGrayscale(mat: Mat): Mat {
    val gray = Mat()
    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

    // 转回RGBA格式以便显示
    val result = Mat()
    Imgproc.cvtColor(gray, result, Imgproc.COLOR_GRAY2RGBA)
    gray.release()
    return result
}
```

#### 锐化处理 (Unsharp Mask)

```kotlin
fun applySharpen(mat: Mat): Mat {
    // 锐化卷积核
    val kernel = Mat(3, 3, CvType.CV_32F)
    kernel.put(0, 0,
        0.0, -1.0, 0.0,
        -1.0, 5.0, -1.0,
        0.0, -1.0, 0.0
    )

    val sharpened = Mat()
    Imgproc.filter2D(mat, sharpened, -1.0, kernel)
    kernel.release()
    return sharpened
}
```

#### 自动对比度增强

```kotlin
fun autoEnhance(mat: Mat, brightness: Double, contrast: Double): Mat {
    val enhanced = Mat()
    // alpha: 对比度, beta: 亮度
    mat.convertTo(enhanced, -1, contrast, (brightness - 1.0) * 128)
    return enhanced
}
```

### 4. 角点排序算法

确保四个角点按正确顺序排列（左上、右上、右下、左下）：

```kotlin
fun orderPoints(points: List<Point>): List<Point> {
    // 按x+y排序，最小的为左上角，最大的为右下角
    val sorted = points.sortedBy { it.x + it.y }
    val topLeft = sorted[0]
    val bottomRight = sorted[3]

    // 按y-x排序，最小的为右上角，最大的为左下角
    val diffSorted = points.sortedBy { it.y - it.x }
    val topRight = diffSorted[0]
    val bottomLeft = diffSorted[3]

    return listOf(topLeft, topRight, bottomRight, bottomLeft)
}
```

## 优势

1. **完全自定义**: UI和处理流程完全可控
2. **算法可控**: 可以针对特定场景优化参数
3. **离线运行**: 不依赖任何网络服务
4. **跨平台**: OpenCV支持多平台
5. **功能强大**: 可扩展更多图像处理功能

## 劣势

1. **开发难度高**: 需要深入了解图像处理
2. **维护成本高**: 需要自己处理各种边界情况
3. **性能优化复杂**: 需要针对不同设备优化
4. **APK体积增加**: OpenCV库约15-20MB

## 适用场景

- 需要深度定制扫描流程
- 需要特殊的图像处理效果
- 不依赖Google Play服务
- 需要跨平台一致性

## 参考链接

- [OpenCV Android 官方文档](https://opencv.org/android/)
- [OpenCV4Android SDK](https://sourceforge.net/projects/opencvlibrary/files/opencv-android/)
- [QuickBird Studios OpenCV](https://github.com/quickbirdstudios/opencv-android)
