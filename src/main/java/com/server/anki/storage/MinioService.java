package com.server.anki.storage;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

@Service
public class MinioService {

    private static final Logger logger = LoggerFactory.getLogger(MinioService.class);

    private final MinioClient minioClient;

    @Value("${minio.bucket.name}")
    private String bucketName;

    @Value("${minio.url}")
    private String minioUrl;

    // 新增配置项
    @Value("${minio.presigned.enabled:true}")
    private boolean presignedEnabled;

    @Value("${minio.presigned.expiry:7}")
    private int presignedExpiryDays;

    public MinioService(@Value("${minio.url}") String minioUrl,
                        @Value("${minio.access.key}") String accessKey,
                        @Value("${minio.secret.key}") String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 上传文件
     * @param file 文件
     * @param folder 文件夹
     * @param fileName 文件名
     * @param permanent 是否永久存储
     * @return 文件访问URL
     */
    public String uploadFile(MultipartFile file, String folder, String fileName, boolean permanent) throws Exception {
        String objectName = folder + "/" + fileName;
        InputStream inputStream = file.getInputStream();

        logger.info("正在上传文件: {} 到 MinIO, permanent: {}", objectName, permanent);

        try {
            // 上传文件
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // 根据是否永久存储返回不同的URL
            String url = permanent ? getPermanentUrl(objectName) : getPresignedUrl(objectName);
            logger.info("文件上传成功. URL: {}", url);
            return url;

        } catch (Exception e) {
            logger.error("文件上传失败: {}", e.getMessage(), e);
            throw new Exception("文件上传失败: " + e.getMessage());
        } finally {
            try {
                inputStream.close();
            } catch (Exception e) {
                logger.warn("关闭输入流失败", e);
            }
        }
    }

    /**
     * 获取永久访问URL
     */
    private String getPermanentUrl(String objectName) {
        return String.format("%s/%s/%s", minioUrl, bucketName, objectName);
    }


    /**
     * 获取预签名URL
     */
    private String getPresignedUrl(String objectName) throws Exception {
        if (!presignedEnabled) {
            return getPermanentUrl(objectName);
        }

        try {
            // 限制最大过期时间为7天
            int expiryDays = Math.min(presignedExpiryDays, 7);

            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiryDays, TimeUnit.DAYS)
                            .build()
            );
        } catch (Exception e) {
            logger.error("生成预签名URL失败: {}", e.getMessage(), e);
            throw new Exception("生成预签名URL失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    public void deleteFile(String fileUrl) throws Exception {
        String objectName = extractObjectNameFromUrl(fileUrl);
        logger.info("正在从MinIO删除文件: {}", objectName);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            logger.info("文件删除成功: {}", objectName);
        } catch (Exception e) {
            logger.error("文件删除失败: {}", e.getMessage(), e);
            throw new Exception("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 从URL中提取对象名
     */
    private String extractObjectNameFromUrl(String fileUrl) {
        try {
            URL url = new URL(fileUrl);
            String path = url.getPath();
            return path.substring(path.indexOf('/', 1) + 1);
        } catch (MalformedURLException e) {
            logger.error("URL解析失败: {}", fileUrl, e);
            return fileUrl;
        }
    }

    /**
     * 检查文件是否存在
     */
    public boolean doesObjectExist(String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}