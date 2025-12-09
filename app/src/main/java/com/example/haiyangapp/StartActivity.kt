package com.example.haiyangapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.haiyangapp.database.AppDatabase
import com.example.haiyangapp.repository.UserRepositoryImpl
import com.example.haiyangapp.viewmodel.AuthState
import com.example.haiyangapp.viewmodel.AuthViewModel
import com.example.haiyangapp.viewmodel.AuthViewModelFactory
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

/**
 * 启动登录注册界面
 */
class StartActivity : AppCompatActivity() {

    private lateinit var viewModel: AuthViewModel
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var btnSubmit: MaterialButton
    private lateinit var tvError: TextView
    private lateinit var progressBar: ProgressBar

    private var loginFragment: LoginFragment? = null
    private var registerFragment: RegisterFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置沉浸式状态栏
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        // 设置状态栏图标为深色（因为背景是浅色）
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        setContentView(R.layout.activity_start)

        // 初始化ViewModel
        val database = AppDatabase.getDatabase(this)
        val userRepository = UserRepositoryImpl(database.userDao())
        val factory = AuthViewModelFactory(userRepository)
        viewModel = ViewModelProvider(this, factory)[AuthViewModel::class.java]

        // 初始化视图
        initViews()
        setupViewPager()
        observeViewModel()
        startAnimations()
        
        // 处理布局重叠问题，给根布局添加padding
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootLayout)) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun startAnimations() {
        // 1. 背景渐变动画
        val rootLayout = findViewById<View>(R.id.rootLayout)
        val animationDrawable = rootLayout.background as? android.graphics.drawable.AnimationDrawable
        animationDrawable?.apply {
            setEnterFadeDuration(3000)
            setExitFadeDuration(3000)
            start()
        }

        // 2. Logo 悬浮呼吸动画
        val logoCard = findViewById<View>(R.id.logoCard)
        val floatAnimator = android.animation.ObjectAnimator.ofFloat(logoCard, "translationY", 0f, -20f, 0f)
        floatAnimator.duration = 3000 // 3秒一个周期
        floatAnimator.repeatCount = android.animation.ValueAnimator.INFINITE
        floatAnimator.repeatMode = android.animation.ValueAnimator.REVERSE
        floatAnimator.interpolator = android.view.animation.AccelerateDecelerateInterpolator()
        floatAnimator.start()
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
        btnSubmit = findViewById(R.id.btnSubmit)
        tvError = findViewById(R.id.tvError)
        progressBar = findViewById(R.id.progressBar)

        btnSubmit.setOnClickListener {     //注册监听器，等待用户点击执行lambda
            tvError.visibility = View.GONE    //隐藏错误提示文本，VISIBLE=可见，Gone不可见
            viewModel.resetState()  //重置状态

            val currentPosition = viewPager.currentItem  //当前索引
            if (currentPosition == 0) {
                // 登录 - 显示确认对话框
                showConfirmDialog(
                    title = "确认登录",
                    message = "确定要登录吗？",
                    onConfirm = {
                        // 用户点击确定后，执行登录
                        loginFragment?.let { fragment ->//let接受一个可空对象，并且自动传参
                            val username = fragment.getUsername()
                            val password = fragment.getPassword()
                            viewModel.login(username, password)
                        }
                    }
                )
            } else {
                // 注册 - 显示确认对话框
                showConfirmDialog(
                    title = "确认注册",
                    message = "确定要注册吗？",
                    onConfirm = {
                        // 用户点击确定后，执行注册
                        registerFragment?.let { fragment ->
                            val username = fragment.getUsername()
                            val password = fragment.getPassword()
                            val confirmPassword = fragment.getConfirmPassword()
                            viewModel.register(username, password, confirmPassword)
                        }
                    }
                )
            }
        }
    }

    private fun setupViewPager() {
//        想象一个相册：
//        - ViewPager2 = 相册本身（展示照片的框架）
//        - Adapter = 管理员（负责提供照片）
//        - Fragment = 一张张照片

        val adapter = AuthPagerAdapter(this)
        viewPager.adapter = adapter

        // 关联TabLayout和ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->//tab,position是TabLayoutMediator内部实现的传参
            tab.text = if (position == 0) "登录" else "注册"
        }.attach()

        // 监听页面切换。回调就是"你先注册一个函数，等某件事发生时，系统会自动调用它"
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                btnSubmit.text = if (position == 0) "登录" else "注册"
                tvError.visibility = View.GONE
                viewModel.resetState()
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                btnSubmit.isEnabled = !isLoading
            }
        }

        lifecycleScope.launch {
            viewModel.authState.collect { state ->
                when (state) {
                    is AuthState.Success -> {
                        // 判断是登录还是注册
                        val currentPosition = viewPager.currentItem
                        if (currentPosition == 0) {
                            // 登录成功，直接跳转到主界面
                            navigateToMain()
                        } else {
                            // 注册成功，显示提示并切换到登录页面
                            Toast.makeText(this@StartActivity, "注册成功！请登录", Toast.LENGTH_SHORT).show()
                            viewPager.currentItem = 0  // 切换到登录页面
                            viewModel.resetState()  // 重置状态
                        }
                    }
                    is AuthState.Error -> {
                        // 显示错误信息
                        tvError.text = state.message
                        tvError.visibility = View.VISIBLE
                    }
                    else -> {
                        // Idle 或 Loading 状态
                    }
                }
            }
        }
    }

    // 显示确认对话框
    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null)
        
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val btnCancel = dialogView.findViewById<View>(R.id.btnDialogCancel)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnDialogConfirm)

        tvTitle.text = title
        tvMessage.text = message

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // 设置背景透明，以便显示我们自定义的圆角背景
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * ViewPager2 适配器
     */
    inner class AuthPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 2//两个页面

        override fun createFragment(position: Int): Fragment {
            return if (position == 0) {
                LoginFragment().also { loginFragment = it }
            } else {
                RegisterFragment().also { registerFragment = it }
            }
        }
    }
}

/**
 * 登录Fragment
 */
class LoginFragment : Fragment() {
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_login, container, false)
        etUsername = view.findViewById(R.id.etUsername)
        etPassword = view.findViewById(R.id.etPassword)
        return view
    }

    fun getUsername(): String = etUsername.text.toString().trim()
    fun getPassword(): String = etPassword.text.toString().trim()
}

/**
 * 注册Fragment
 */
class RegisterFragment : Fragment() {
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_register, container, false)
        etUsername = view.findViewById(R.id.etUsername)
        etPassword = view.findViewById(R.id.etPassword)
        etConfirmPassword = view.findViewById(R.id.etConfirmPassword)
        return view
    }

    fun getUsername(): String = etUsername.text.toString().trim()
    fun getPassword(): String = etPassword.text.toString().trim()
    fun getConfirmPassword(): String = etConfirmPassword.text.toString().trim()
}
