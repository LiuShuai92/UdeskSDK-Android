package cn.udesk.activity;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import udesk.core.LocalManageUtil;

public class UdeskBaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 调用适配方法
        handleWindowInsets();
    }

    private void handleWindowInsets() {
        // 获取 Activity 的根内容视图
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                // 获取系统栏（状态栏和导航栏）占据的范围
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                // 通过设置 Padding 的方式，将内容推回安全区域，防止被状态栏遮挡
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocalManageUtil.setLocal(newBase));
    }
}