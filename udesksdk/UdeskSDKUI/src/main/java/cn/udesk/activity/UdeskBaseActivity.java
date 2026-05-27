package cn.udesk.activity;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;

import udesk.core.LocalManageUtil;

public class UdeskBaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 处理 Android 15/16 强制全屏导致的遮挡问题
        handleWindowInsets();
    }

    private void handleWindowInsets() {
        // 获取内容根视图（android.R.id.content）
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
                // 获取系统栏（状态栏、导航栏）的内边距
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                // 为根视图设置 Padding，避开系统栏
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
