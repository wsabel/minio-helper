package com.github.qq120011676.minio.server.controller;

import com.github.qq120011676.ladybird.web.exception.RestfulExceptionHelper;
import com.github.qq120011676.minio.properties.MinIOProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.minio.MinioClient;
import io.minio.ObjectStat;
import io.minio.errors.*;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.xmlpull.v1.XmlPullParserException;

import javax.annotation.Resource;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("file")
public class MinioController {
    @Resource
    private RestfulExceptionHelper restfulExceptionHelper;
    @Resource
    private MinioClient minioClient;
    @Resource
    private MinIOProperties minIOProperties;

    @PostMapping("upload")
    public String upload(MultipartFile file) throws IOException, XmlPullParserException, NoSuchAlgorithmException, InvalidKeyException, InvalidArgumentException, InvalidResponseException, InternalException, NoResponseException, InvalidBucketNameException, InsufficientDataException, ErrorResponseException {
        return this.minioUpload(file).toString();
    }

    @PostMapping("uploads")
    public String uploads(MultipartFile[] files) throws IOException, XmlPullParserException, NoSuchAlgorithmException, InvalidKeyException, InvalidArgumentException, InvalidResponseException, InternalException, NoResponseException, InvalidBucketNameException, InsufficientDataException, ErrorResponseException {
        if (files == null) {
            throw this.restfulExceptionHelper.getRestfulRuntimeException("upload_no_file");
        }
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw this.restfulExceptionHelper.getRestfulRuntimeException("upload_no_file");
            }
        }
        JsonArray jsonArray = new JsonArray();
        for (MultipartFile file : files) {
            jsonArray.add(this.minioUpload(file));
        }
        return jsonArray.toString();
    }

    private JsonObject minioUpload(MultipartFile file) throws IOException, XmlPullParserException, NoSuchAlgorithmException, InvalidKeyException, InvalidArgumentException, InvalidResponseException, InternalException, NoResponseException, InvalidBucketNameException, InsufficientDataException, ErrorResponseException {
        if (file.isEmpty()) {
            throw this.restfulExceptionHelper.getRestfulRuntimeException("upload_no_file");
        }
        Map<String, String> map = new HashMap<>();
        map.put("filename", file.getOriginalFilename());
        String objectName = MessageFormat.format("{0}_{1}", UUID.randomUUID().toString(), file.getOriginalFilename());
        this.minioClient.putObject(this.minIOProperties.getBucket(), objectName, file.getInputStream(), file.getSize(), map, null, file.getContentType());
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("filename", objectName);
        return jsonObject;
    }

    @RequestMapping("view/{filename}")
    public ResponseEntity<InputStreamResource> view(@PathVariable String filename) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, InvalidArgumentException, InvalidResponseException, InternalException, NoResponseException, InvalidBucketNameException, XmlPullParserException, ErrorResponseException {
        ObjectStat objectStat = this.minioClient.statObject(this.minIOProperties.getBucket(), filename);
        return ResponseEntity
                .ok()
                .contentLength(objectStat.length())
                .contentType(MediaType.valueOf(objectStat.contentType()))
                .body(new InputStreamResource(this.minioClient.getObject(this.minIOProperties.getBucket(), filename)));
    }

    @RequestMapping("downloadByUrl/{filename}")
    public String downloadByUrl(@PathVariable String filename) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, InvalidExpiresRangeException, InvalidResponseException, InternalException, NoResponseException, InvalidBucketNameException, XmlPullParserException, ErrorResponseException {
        String url = this.minioClient.presignedGetObject(this.minIOProperties.getBucket(), filename);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("url", url);
        return jsonObject.toString();
    }

    @GetMapping("download/{filename}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String filename) throws IOException, InvalidKeyException, NoSuchAlgorithmException, InsufficientDataException, InvalidResponseException, InternalException, NoResponseException, InvalidBucketNameException, XmlPullParserException, ErrorResponseException, InvalidArgumentException {
        ObjectStat objectStat = this.minioClient.statObject(this.minIOProperties.getBucket(), filename);
        String saveFilename = objectStat.name();
        String[] filenameArray = filename.split("_", 2);
        if (filenameArray.length > 1) {
            saveFilename = filenameArray[1];
        }
        List<String> filenames = objectStat.httpHeaders().get("filename");
        if (filenames != null && !filenames.isEmpty()) {
            saveFilename = filenames.get(0);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", MessageFormat.format("attachment; filename=\"{0}\"", saveFilename));
        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.valueOf("application/force-download"))
                .body(new InputStreamResource(this.minioClient.getObject(this.minIOProperties.getBucket(), filename)));
    }
}
