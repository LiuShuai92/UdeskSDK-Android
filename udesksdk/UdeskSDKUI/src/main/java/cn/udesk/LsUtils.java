package cn.udesk;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LsUtils {

    private static final String TAG = "FileSizeUtils";

    /**
     * 获取文件大小（字节）
     * 支持 Uri 和 绝对路径
     *
     * @param context 上下文
     * @param source  可以是 Uri 对象，也可以是 String 类型的路径
     * @return 文件大小（byte），获取失败返回 -1
     */
    public static long getFileSize(Context context, Object source) {
        if (source == null) {
            return -1;
        }

        if (source instanceof Uri) {
            return getFileSizeFromUri(context, (Uri) source);
        } else if (source instanceof String) {
            return getFileSizeFromPath((String) source);
        }

        return -1;
    }

    /**
     * 根据文件路径获取大小
     */
    private static long getFileSizeFromPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return -1;
        }
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            return file.length();
        }
        return -1;
    }

    /**
     * 核心方法：根据 Uri 获取大小
     * 兼容 content:// 和 file:// 协议
     */
    private static long getFileSizeFromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return -1;
        }

        String scheme = uri.getScheme();

        // 1. 如果是 file:// 协议，直接转 File 处理
        if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            return getFileSizeFromPath(uri.getPath());
        }

        // 2. 如果是 content:// 协议 (Android 7.0+ 及分区存储主要场景)
        if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            long size = -1;

            // 策略 A：尝试通过 OpenableColumns 查询（标准方式，效率高）
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Query OpenableColumns failed: " + e.getMessage());
            }

            // 如果策略 A 成功拿到有效大小，直接返回
            if (size > 0) {
                return size;
            }

            // 策略 B：兜底方案，通过文件描述符读取（更健壮，适用于不规范的 Provider）
            // 很多第三方 App (如某些文件管理器) 共享的文件可能查询不到 OpenableColumns
            try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
                if (pfd != null) {
                    size = pfd.getStatSize();
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, "File not found via Descriptor: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Get size via Descriptor failed: " + e.getMessage());
            }

            return size;
        }

        return -1;
    }
    /**
     * 替换 BitmapFactory.decodeFile
     * 兼容 Uri 对象、文件路径 String、以及 content:// 格式的 String
     *
     * @param context 上下文
     * @param source  可以是 Uri, File, 或者 String (路径/Uri字符串)
     * @param options BitmapFactory.Options (可以为 null)
     * @return Bitmap
     */
    public static Bitmap decodeFlexible(Context context, Object source, BitmapFactory.Options options) {
        Uri uri = null;

        // 1. 统一转换为 Uri
        if (source instanceof Uri) {
            uri = (Uri) source;
        } else if (source instanceof String) {
            String path = (String) source;
            if (TextUtils.isEmpty(path)) return null;

            if (path.startsWith("content://") || path.startsWith("file://")) {
                uri = Uri.parse(path);
            } else {
                // 假设是绝对路径 /storage/emulated/...
                uri = Uri.fromFile(new File(path));
            }
        } else if (source instanceof File) {
            uri = Uri.fromFile((File) source);
        }

        if (uri == null) return null;

        // 2. 使用 FileDescriptor 解码
        ParcelFileDescriptor pfd = null;
        try {
            pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd != null) {
                // 核心替换代码：decodeFileDescriptor
                // 注意：第二个参数传 null (rect padding)，通常不需要
                return BitmapFactory.decodeFileDescriptor(pfd.getFileDescriptor(), null, options);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 3. 极其重要：必须关闭 ParcelFileDescriptor，否则会内存泄漏
            try {
                if (pfd != null) {
                    pfd.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public static InputStream getInputStreamSafe(Context context, Uri uri) {
        try {
            return context.getContentResolver().openInputStream(uri);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static File uriToFile(Context context, Uri uri) {
        File file = null;
        if (uri == null) return null;

        try {
            ContentResolver resolver = context.getContentResolver();

            // 1. 尝试获取原文件名 (这对于上传很重要，服务器可能需要后缀名)
            String fileName = getFileName(context, uri);
            if (fileName == null) {
                // 如果获取不到，生成一个随机文件名
                fileName = "temp_upload_" + System.currentTimeMillis();
            }

            // 2. 在 Cache 目录下创建临时文件
            File cacheDir = context.getCacheDir();
            file = new File(cacheDir, fileName);

            // 如果文件已存在且大小大于0，视情况可以直接返回（看你业务是否允许复用）
            // 这里为了保险，选择覆盖或者先删除
            if (file.exists()) {
                file.delete();
            }

            // 3. 核心：通过流拷贝内容
            InputStream inputStream = resolver.openInputStream(uri);
            OutputStream outputStream = new FileOutputStream(file);

            if (inputStream != null) {
                byte[] buffer = new byte[4 * 1024]; // 4k buffer
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                outputStream.flush();
            }

            // 关流
            if (inputStream != null) inputStream.close();
            outputStream.close();

            return file;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    // 辅助方法：获取真实文件名
    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    // 尝试从 OpenableColumns 获取文件名
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if(index >= 0) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
}