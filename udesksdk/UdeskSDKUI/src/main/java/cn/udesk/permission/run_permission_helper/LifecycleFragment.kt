package cn.udesk.permission.run_permission_helper
import android.app.Activity
import android.app.Fragment
import android.content.Intent
import android.os.Bundle
import java.util.HashSet

/**
 * Description:     LifecycleFragment
 * Author:         刘帅
 * CreateDate:     2022/1/14
 */
class LifecycleFragment : Fragment() {
    private val callBacks: MutableSet<CallBack> = HashSet()
    fun addCallBack(callBack: CallBack) {
        callBacks.add(callBack)
    }

    fun removeCallBack(callBack: CallBack?) {
        callBacks.remove(callBack)
        if (callBacks.isEmpty()) {
            removeFragment()
        }
    }

    fun clearCallBack() {
        callBacks.clear()
        removeFragment()
    }

    private fun removeFragment() {
        activity?.fragmentManager?.apply {
            beginTransaction().remove(this@LifecycleFragment).commitAllowingStateLoss()
            executePendingTransactions()
        }
    }

    interface CallBack {
        fun onCreate(savedInstanceState: Bundle?)
        fun onActivityCreated(savedInstanceState: Bundle?)
        fun onStart()
        fun onResume()
        fun onPause()
        fun onStop()
        fun onDestroyView()
        fun onDestroy()
        fun onSaveInstanceState(outState: Bundle?)
        fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        )

        fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onCreate(savedInstanceState)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onActivityCreated(savedInstanceState)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onStart()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onResume()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onPause()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onDestroyView()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onDestroy()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onStop()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onSaveInstanceState(outState)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (callBacks.isNotEmpty()) {
            for (callBack in callBacks) {
                callBack.onActivityResult(requestCode, resultCode, data)
            }
        }
    }



    companion object {
        @JvmStatic
        fun attach(activity: Activity): LifecycleFragment {
            val fragmentManager = activity.fragmentManager
            val tag = activity.componentName.toShortString()
            var fragment = fragmentManager.findFragmentByTag(tag)
            if (null == fragment) {
                fragment = LifecycleFragment()
                fragmentManager.beginTransaction().add(fragment, tag).commitAllowingStateLoss()
                fragmentManager.executePendingTransactions()
            }
            val lifeCycleFragment: LifecycleFragment = if (fragment is LifecycleFragment) {
                fragment
            } else {
                throw IllegalStateException("A class " + fragment.javaClass.name + " with tag '" + tag + "' already exists.")
            }
            return lifeCycleFragment
        }
    }
}