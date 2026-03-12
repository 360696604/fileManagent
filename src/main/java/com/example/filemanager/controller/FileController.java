package com.example.filemanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class FileController {

    /**
     * 获取文件存储目录（程序运行目录下的files文件夹）
     */
    private File getUploadDir() {
        String userDir = System.getProperty("user.dir");
        File filesDir = new File(userDir, "files");
        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }
        return filesDir;
    }

    /**
     * 文件信息类
     */
    public static class FileInfo {
        private String name;
        private long size;
        private String sizeFormatted;
        private long lastModified;

        public FileInfo(String name, long size, long lastModified) {
            this.name = name;
            this.size = size;
            this.lastModified = lastModified;
            this.sizeFormatted = formatFileSize(size);
        }

        private String formatFileSize(long size) {
            if (size < 1024) {
                return size + " B";
            } else if (size < 1024 * 1024) {
                return String.format("%.2f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", size / (1024.0 * 1024));
            } else {
                return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
            }
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public String getSizeFormatted() {
            return sizeFormatted;
        }

        public long getLastModified() {
            return lastModified;
        }
    }

    /**
     * 获取文件列表页面
     */
    @GetMapping("/")
    public String index(Model model, @RequestParam(required = false) String search) {
        File uploadDir = getUploadDir();
        File[] files = uploadDir.listFiles();

        List<FileInfo> fileList = new ArrayList<>();

        if (files != null) {
            List<FileInfo> tempList = Arrays.stream(files)
                    .filter(file -> file.isFile() && !file.isHidden())
                    .map(file -> new FileInfo(
                            file.getName(),
                            file.length(),
                            file.lastModified()
                    ))
                    .collect(Collectors.toList());

            // 如果有搜索关键字，进行过滤
            if (search != null && !search.trim().isEmpty()) {
                tempList = tempList.stream()
                        .filter(f -> f.getName().toLowerCase().contains(search.toLowerCase()))
                        .collect(Collectors.toList());
            }

            // 按最后修改时间倒序排列
            tempList.sort((a, b) -> Long.compare(b.getLastModified(), a.getLastModified()));
            fileList = tempList;
        }

        model.addAttribute("files", fileList);
        model.addAttribute("currentDir", uploadDir.getAbsolutePath());
        model.addAttribute("search", search);

        return "index";
    }

    /**
     * 下载文件
     */
    @GetMapping("/download/{filename}")
    public void download(@PathVariable String filename, HttpServletResponse response) {
        try {
            File uploadDir = getUploadDir();
            Path filePath = Paths.get(uploadDir.getAbsolutePath(), filename);
            File file = filePath.toFile();

            if (!file.exists() || !file.isFile()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("文件不存在");
                return;
            }

            // 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLengthLong(file.length());

            // 写入文件内容
            Files.copy(filePath, response.getOutputStream());
            response.getOutputStream().flush();

        } catch (IOException e) {
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("下载失败: " + e.getMessage());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, Model model) {
        if (file.isEmpty()) {
            model.addAttribute("message", "请选择要上传的文件");
            model.addAttribute("messageType", "danger");
            return index(model, null);
        }

        try {
            File uploadDir = getUploadDir();
            String originalFilename = file.getOriginalFilename();
            Path filePath = Paths.get(uploadDir.getAbsolutePath(), originalFilename);

            // 如果文件已存在，自动重命名（增加年月日时分秒）
            File existingFile = filePath.toFile();
            if (existingFile.exists()) {
                // 分离文件名和扩展名
                String nameWithoutExt;
                String extension;
                int dotIndex = originalFilename.lastIndexOf('.');
                if (dotIndex > 0) {
                    nameWithoutExt = originalFilename.substring(0, dotIndex);
                    extension = originalFilename.substring(dotIndex);
                } else {
                    nameWithoutExt = originalFilename;
                    extension = "";
                }

                // 生成新的文件名
                String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
                String newFilename = nameWithoutExt + "_" + timestamp + extension;
                filePath = Paths.get(uploadDir.getAbsolutePath(), newFilename);
                originalFilename = newFilename;
            }

            // 保存文件
            file.transferTo(filePath.toFile());

            model.addAttribute("message", "文件上传成功: " + originalFilename);
            model.addAttribute("messageType", "success");

        } catch (IOException e) {
            model.addAttribute("message", "上传失败: " + e.getMessage());
            model.addAttribute("messageType", "danger");
        }

        return index(model, null);
    }

    /**
     * 删除文件
     */
    @PostMapping("/delete/{filename}")
    public String delete(@PathVariable String filename, Model model) {
        try {
            File uploadDir = getUploadDir();
            Path filePath = Paths.get(uploadDir.getAbsolutePath(), filename);
            File file = filePath.toFile();

            if (file.exists() && file.isFile()) {
                if (file.delete()) {
                    model.addAttribute("message", "文件删除成功: " + filename);
                    model.addAttribute("messageType", "success");
                } else {
                    model.addAttribute("message", "文件删除失败");
                    model.addAttribute("messageType", "danger");
                }
            } else {
                model.addAttribute("message", "文件不存在");
                model.addAttribute("messageType", "danger");
            }
        } catch (Exception e) {
            model.addAttribute("message", "删除失败: " + e.getMessage());
            model.addAttribute("messageType", "danger");
        }

        return index(model, null);
    }
}