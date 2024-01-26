package cn.udesk.permission.run_permission_helper

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.Fragment
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.ActivityCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import cn.udesk.R
import cn.udesk.permission.run_permission_helper.LifecycleFragment.Companion.attach
import cn.udesk.permission.run_permission_helper.view.RoundContainerLayout
import java.util.Arrays
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Description: RunPermissionHelper
 * Author: 刘帅
 * CreateDate: 2021/11/2
 */
object RunPermissionHelper {
    private const val TAG = "RunPermissionHelper"
    private const val OVERLAY_PERMISSION_TIP = "OVERLAY_PERMISSION_TIP"
    private const val OVERLAY_PERMISSION = "OVERLAY_PERMISSION"
    private var applicationPermissions: List<String>? = null
    private val atomicRequestCode = AtomicInteger(500)

    /**
     * 获取不会重复的请求码
     *
     * @return 请求码
     */
    @Synchronized
    fun generatePermissionRequestCode(): Int {
        return atomicRequestCode.addAndGet(1)
    }

    /**
     * 悬浮窗权限申请
     *
     * @param activity                     Context
     * @param direction                    关于权限需求原因的描述
     * @param onRequestPermissionsListener 成功或失败的回调
     */
    fun requestOverlayPermission(
        activity: Activity, direction: String?,
        onRequestPermissionsListener: OnRequestPermissionsListener
    ) {
        val originRequestCode = generatePermissionRequestCode()
        //        final String[] permission = new String[]{Manifest.permission.SYSTEM_ALERT_WINDOW};
        if (checkOverlayPermission(activity)) {
            onRequestPermissionsListener.onPermissionsGranted(originRequestCode, null, null)
        } else {
            AlertDialog.Builder(activity)
                .setMessage(direction)
                .setPositiveButton("开启") { dialog: DialogInterface?, which: Int ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // 6.0动态申请悬浮窗权限
                        if (!Settings.canDrawOverlays(activity)) {
                            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            intent.data = Uri.parse("package:" + activity.packageName)
                            activity.startActivityForResult(intent, originRequestCode)
                            attach(activity).addCallBack(object : CallBack {
                                override fun onActivityResult(
                                    requestCode: Int,
                                    resultCode: Int,
                                    data: Intent?
                                ) {
                                    if (originRequestCode == requestCode) {
                                        if (checkOverlayPermission(activity)) {
                                            onRequestPermissionsListener.onPermissionsGranted(
                                                originRequestCode,
                                                null,
                                                null
                                            )
                                        } else {
                                            onRequestPermissionsListener.onPermissionsDenied(
                                                originRequestCode,
                                                null,
                                                null
                                            )
                                        }
                                    }
                                }
                            })
                            return@setPositiveButton
                        }
                    } else {
                        if (!checkOp(activity, 24)) {
                            Toast.makeText(
                                activity,
                                "进入设置页面失败,请手动开启悬浮窗权限",
                                Toast.LENGTH_SHORT
                            )
                                .show()
                            onRequestPermissionsListener.onPermissionsDenied(
                                originRequestCode,
                                null,
                                null
                            )
                            return@setPositiveButton
                        }
                    }
                    onRequestPermissionsListener.onPermissionsGranted(originRequestCode, null, null)
                }
                .setNegativeButton("暂不开启") { dialog: DialogInterface?, which: Int ->
                    onRequestPermissionsListener.onPermissionsDenied(
                        originRequestCode,
                        null,
                        null
                    )
                }
                .show()
        }
    }

    /**
     * 权限申请
     *
     * @param activity                     当前的Activity
     * @param direction                    关于权限需求原因的描述
     * @param onRequestPermissionsListener 成功或失败的回调
     * @param permissions                  需要申请的权限
     */
    fun requestRunPermission(
        activity: Activity?,
        needDialog: Boolean,
        showDirection: Boolean,
        direction: String?,
        onRequestPermissionsListener: OnRequestPermissionsListener?,
        vararg permissions: String?
    ) {
        val requestCode = generatePermissionRequestCode()
        requestRunPermission(
            activity,
            needDialog,
            showDirection,
            requestCode,
            direction,
            onRequestPermissionsListener,
            *permissions
        )
    }

    /**
     * 权限申请
     *
     * @param activity                     当前的Activity
     * @param requestCode                  请求码 (可以使用 RunPermissionHelper.generatePermissionRequestCode())
     * @param direction                    关于权限需求原因的描述
     * @param onRequestPermissionsListener 成功或失败的回调
     * @param permissions                  需要申请的权限
     */
    fun requestRunPermission(
        activity: Activity?,
        needDialog: Boolean,
        showDirection: Boolean,
        requestCode: Int,
        direction: String?,
        onRequestPermissionsListener: OnRequestPermissionsListener?,
        vararg permissions: String?
    ) {
        if (activity != null) {
            val fragmentManager = activity.fragmentManager
            var fragment = fragmentManager.findFragmentByTag("LifeCycle")
            if (fragment == null) {
                fragment = PermissionFragment()
                fragmentManager.beginTransaction().add(fragment, "LifeCycle")
                    .commitAllowingStateLoss()
                fragmentManager.executePendingTransactions()
            }
            if (fragment is PermissionFragment) {
                fragment.requestRunPermission(
                    requestCode,
                    needDialog,
                    showDirection,
                    direction,
                    onRequestPermissionsListener,
                    *permissions as Array<out String>
                )
            } else {
                throw IllegalStateException("A 'LifeCycle' for class " + fragment.javaClass.name + " already exists.")
            }
        }
    }

    /**
     * API <18，默认有悬浮窗权限，不需要处理。无法接收无法接收触摸和按键事件，不需要权限和无法接受触摸事件的源码分析
     * API >= 19 ，可以接收触摸和按键事件
     * API >=23，需要在manifest中申请权限，并在每次需要用到权限的时候检查是否已有该权限，因为用户随时可以取消掉。
     * API >25，TYPE_TOAST 已经被谷歌制裁了，会出现自动消失的情况
     */
    fun checkOp(context: Context, op: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val manager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            try {
                val method = AppOpsManager::class.java.getDeclaredMethod(
                    "checkOp",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                return AppOpsManager.MODE_ALLOWED == method.invoke(
                    manager,
                    op,
                    Binder.getCallingUid(),
                    context.packageName
                ) as Int
            } catch (e: Exception) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
        return true
    }

    fun checkOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // 6.0动态申请悬浮窗权限
            Settings.canDrawOverlays(context)
        } else {
            checkOp(context, 24)
        }
    }

    /**
     * Return the permissions used in application.
     *
     * @return the permissions used in application
     */
    fun getPermissions(context: Context?): List<String>? {
        return if (applicationPermissions != null) {
            applicationPermissions
        } else try {
            val permissions = context!!.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_PERMISSIONS
            ).requestedPermissions
                ?: return emptyList()
            Arrays.asList(*permissions).also { applicationPermissions = it }
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 获取多个权限的中文描述
     *
     * @param permissions 权限
     * @return 权限的中文描述
     */
    fun getPermissionNames(vararg permissions: String?): Array<String?>? {
        if (permissions == null) return null
        val permissionNames = arrayOfNulls<String>(permissions.size)
        for (i in permissions.indices) {
            val permission = permissions[i]
            permissionNames[i] = getPermissionName(permission)
        }
        return permissionNames
    }

    /**
     * 获取单个权限中文描述
     *
     * @param permission 权限
     * @return 权限的中文描述
     */
    fun getPermissionName(permission: String?): String {
        val permissionName: String
        permissionName = when (permission) {
            Manifest.permission.CAMERA -> "拍照或录像权限"
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE -> "存储权限"
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> "读写共享存储内容"
            Manifest.permission.ACCESS_COARSE_LOCATION -> "获取大致位置信息权限"
            Manifest.permission.ACCESS_FINE_LOCATION -> "获取确切位置信息权限"
            Manifest.permission.READ_PHONE_STATE -> "获取手机状态权限"
            Manifest.permission.SYSTEM_ALERT_WINDOW -> "获取悬浮窗权"
            Manifest.permission.CALL_PHONE -> "拨打电话权限"
            Manifest.permission.BLUETOOTH_ADVERTISE -> "使当前设备可被其他蓝牙设备检测到权限"
            Manifest.permission.BLUETOOTH_CONNECT -> "与已配对的蓝牙设备通信权限"
            Manifest.permission.BLUETOOTH_SCAN -> "查找蓝牙设备权限"
            else -> "权限"
        }
        return permissionName
    }

    /**
     * 获得状态栏高度
     *
     * @param context
     * @return
     */
    fun getStatusHeight(context: Activity?): Int {
        val frame = Rect()
        context!!.window.decorView.getWindowVisibleDisplayFrame(frame)
        return frame.top
        /*int statusHeight = -1;
        try {
            Class<?> clazz = Class.forName("com.android.internal.R$dimen");
            Object object = clazz.newInstance();
            int height = Integer.parseInt(clazz.getField("status_bar_height")
                    .get(object).toString());
            statusHeight = context.getResources().getDimensionPixelSize(height);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return statusHeight;*/
    }

    interface OnRequestPermissionsListener {
        /**
         * 授权通过之后的操作
         *
         * @param requestCode     请求码
         * @param permissions     请求的权限
         * @param permissionNames 请求的权限
         */
        fun onPermissionsGranted(
            requestCode: Int,
            permissions: Array<String>?,
            permissionNames: Array<String?>?
        )

        /**
         * 授权失败之后的操作
         *
         * @param requestCode           请求码
         * @param deniedPermissions     被拒绝的权限
         * @param deniedPermissionNames 被拒绝的权限
         */
        fun onPermissionsDenied(
            requestCode: Int,
            deniedPermissions: Array<String>?,
            deniedPermissionNames: Array<String?>?
        ) {
        }
    }

    class PermissionFragment : Fragment() {
        private var mListener: OnRequestPermissionsListener? = null
        private val SETTING_RESULT_CODE = 1000
        private var rlDirection: RoundContainerLayout? = null
        private var isOnRequestRunPermission = false
        private lateinit var originalPermissions: Array<String>
        private var direction: String? = null
        private var statusBarHeight = 0
        private var needDialog = false
        val handler = Handler(Looper.getMainLooper());
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            /*if (savedInstanceState != null) {
                originalPermissions = savedInstanceState.getStringArray("permissions");
                direction = savedInstanceState.getString("direction", null);
            }*/statusBarHeight = getStatusHeight(activity)
        }

        /*@Override
        public void onSaveInstanceState(final Bundle outState) {
            super.onSaveInstanceState(outState);
            if (originalPermissions != null) {
                outState.putStringArray("permissions", originalPermissions);
            }
            if (direction != null) {
                outState.putString("direction", direction);
            }
        }*/
        override fun onResume() {
            super.onResume()
            if (isOnRequestRunPermission) {
                rlDirection!!.isEnabled = false
            }
        }

        override fun onPause() {
            super.onPause()
            if (isOnRequestRunPermission) {
                rlDirection!!.isEnabled = true
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            return super.onCreateView(inflater, container, savedInstanceState)
        }

        override fun onAttach(context: Context) {
            super.onAttach(context)
        }

        override fun onDetach() {
            super.onDetach()
            mListener = null
        }

        /**
         * 权限申请
         *
         * @param requestCode 请求码
         * @param permissions 需要请求的权限
         */
        fun requestRunPermission(
            requestCode: Int,
            needDialog: Boolean,
            showDirection: Boolean,
            direction: String?,
            onRequestPermissionsListener: OnRequestPermissionsListener?,
            vararg permissions: String
        ) {
            val permissions = permissions as Array<String>
            mListener = onRequestPermissionsListener
            if (permissions.size == 0) {
                onPermissionsGranted(requestCode, permissions)
                return
            }
            this.needDialog = needDialog
            isOnRequestRunPermission = true
            originalPermissions = permissions
            this.direction = direction
            val decorView = activity.window.decorView as ViewGroup
            val rootView = LayoutInflater.from(activity).inflate(R.layout.udesk_fragment_permission, null)
            handler.postDelayed({
                decorView.addView(rootView)
            }, 300)
            rlDirection = rootView.findViewById(R.id.rl_direction)
            val tvDirection = rootView.findViewById<TextView>(R.id.tv_direction_content)
            rootView.setPadding(0, statusBarHeight, 0, 0)
            if (Build.VERSION.SDK_INT >= 23) {
                val deniedPermissionList: MutableList<String?> = LinkedList()
                val invalidPermissions: MutableList<String> = LinkedList()
                var shouldShowRequestPermissionList = showDirection
                for (permission in permissions) {
                    if (getPermissions(activity)!!.contains(permission)) {
                        val i = ActivityCompat.checkSelfPermission(
                            activity!!, permission
                        )
                        if (i != PackageManager.PERMISSION_GRANTED) {
                            var deniedPermission: String? = null
                            var invalidPermission: String? = null
                            when (permission) {
                                Manifest.permission.READ_MEDIA_IMAGES,
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.POST_NOTIFICATIONS -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        deniedPermission = permission
                                    } else {
                                        invalidPermission = permission
                                    }
                                }

                                Manifest.permission.ACTIVITY_RECOGNITION,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        deniedPermission = permission
                                    } else {
                                        invalidPermission = permission
                                    }
                                }

                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                                Manifest.permission.BLUETOOTH_ADVERTISE -> {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        deniedPermission = permission
                                    } else {
                                        invalidPermission = permission
                                    }
                                }

                                else -> deniedPermission = permission
                            }
                            if (deniedPermission != null) {
                                Log.d(
                                    TAG,
                                    "requestRunPermission: deniedPermission = $deniedPermission"
                                )
                                deniedPermissionList.add(deniedPermission)
                            }
                            if (invalidPermission != null) {
                                Log.d(
                                    TAG,
                                    "requestRunPermission: invalidPermission = $invalidPermission"
                                )
                                invalidPermissions.add(invalidPermission)
                            }
                            if (!shouldShowRequestPermissionList) {
                                shouldShowRequestPermissionList =
                                    ActivityCompat.shouldShowRequestPermissionRationale(
                                        activity!!, permission
                                    )
                            }
                        }
                    }
                }
                if (deniedPermissionList.isNotEmpty()) {
                    if (shouldShowRequestPermissionList) {
                        if (direction.isNullOrEmpty()) {
                            rlDirection?.visibility = View.GONE
                        } else {
                            rlDirection?.visibility = View.VISIBLE
                            rlDirection?.isEnabled = true
                            tvDirection.text = direction
                        }
                    }
                    requestPermissions(deniedPermissionList.toTypedArray(), requestCode)
                } else {
                    //表示全都授权了
                    onPermissionsGranted(requestCode, permissions)
                }
            } else {
                onPermissionsGranted(requestCode, permissions)
            }
        }

        private fun onPermissionsGranted(requestCode: Int, permissions: Array<String>) {
            mListener!!.onPermissionsGranted(
                requestCode,
                permissions,
                getPermissionNames(*permissions)
            )
        }

        private fun onPermissionsDenied(requestCode: Int, deniedPermissions: Array<String>) {
            if (needDialog) {
                val deniedMsg = StringBuilder()
                for (i in deniedPermissions.indices) {
                    val permission = deniedPermissions[i]
                    deniedMsg.append(getPermissionName(permission))
                    if (i != deniedPermissions.size - 1) deniedMsg.append("、")
                }
                deniedMsg.append("被拒绝，请在-应用设置-权限-中，允许应用使用该权限。")
                val permissionDialog = AlertDialog.Builder(
                    activity,
                    R.style.Theme_AppCompat_Dialog
                ) //                    .setTitle("缺少权限！")
                    .setMessage(deniedMsg)
                    .setPositiveButton("前往设置") { dialog: DialogInterface?, which: Int ->
                        val packageURI = Uri.parse("package:" + activity!!.packageName)
                        val intent =
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageURI)
                        startActivityForResult(intent, SETTING_RESULT_CODE)
                    }
                    .setNegativeButton("取消") { dialog: DialogInterface?, which: Int ->
                        mListener!!.onPermissionsDenied(
                            requestCode,
                            deniedPermissions,
                            getPermissionNames(*deniedPermissions)
                        )
                    }
                    .create()
                permissionDialog.show()
            } else {
                mListener!!.onPermissionsDenied(
                    requestCode,
                    deniedPermissions,
                    getPermissionNames(*deniedPermissions)
                )
            }
        }

        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            val deniedPermissions = ArrayList<String>()
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }
            isOnRequestRunPermission = false
            rlDirection!!.visibility = View.GONE
            if (deniedPermissions.size > 0) {
                onPermissionsDenied(requestCode, deniedPermissions.toTypedArray())
            } else {
                onPermissionsGranted(requestCode, permissions)
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == SETTING_RESULT_CODE) {
                requestRunPermission(
                    requestCode,
                    needDialog,
                    false,
                    direction,
                    mListener,
                    *originalPermissions
                )
            }
        }
    }

    internal interface CallBack : LifecycleFragment.CallBack {
        override fun onCreate(savedInstanceState: Bundle?) {}
        override fun onActivityCreated(savedInstanceState: Bundle?) {}
        override fun onStart() {}
        override fun onResume() {}
        override fun onPause() {}
        override fun onStop() {}
        override fun onDestroyView() {}
        override fun onDestroy() {}
        override fun onSaveInstanceState(outState: Bundle?) {}
        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        ) {
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {}
    }
}