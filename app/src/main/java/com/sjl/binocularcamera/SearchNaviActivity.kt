package com.sjl.binocularcamera

import android.app.Application
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.Gravity
import androidx.appcompat.app.AppCompatActivity
import com.ai.face.faceSearch.search.FaceSearchImagesManger
import com.airbnb.lottie.LottieAnimationView
import com.lzf.easyfloat.EasyFloat
import com.sjl.binocularcamera.FaceSearchApplication.CACHE_SEARCH_FACE_DIR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream


/**
 * 演示导航Navi，主要界面App
 *
 *
 */
class SearchNaviActivity : AppCompatActivity(){

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    /**
     * companion object 辅助验证
     *
     */
    companion object {
        fun showAppFloat(context: Context) {
            if (EasyFloat.getFloatView("speed")?.isShown == true) return
            EasyFloat.with(context)
                .setTag("speed")
                .setGravity(Gravity.CENTER, 0, 0)
                .setDragEnable(false)
                .setLayout(R.layout.float_loading) {
                    val entry: LottieAnimationView = it.findViewById(R.id.entry)
                    entry.setAnimation(R.raw.loading2)
                    entry.loop(true)
                    entry.playAnimation()
                }
                .show()
        }

        private fun getBitmapFromAsset(assetManager: AssetManager, strName: String): Bitmap? {
            val istr: InputStream
            var bitmap: Bitmap?
            try {
                istr = assetManager.open(strName)
                bitmap = BitmapFactory.decodeStream(istr)
            } catch (e: IOException) {
                return null
            }
            return bitmap
        }


        /**
         * 拷贝工程Assets 目录下的人脸图来演示人脸搜索，实际上你的业务人脸可能是在局域网服务器或只能本地录入
         *
         * 只有Assets 肯定搜索不到对应的人脸（也许有BUG 也能） 这个时候你要再录入一张你的人脸照片
         * FaceImageEditActivity 中的拍照按钮可以触发自拍
         *
         *
         *
         */
        suspend fun copyManyTestFaceImages(context: Application) = withContext(Dispatchers.IO) {
            val assetManager = context.assets
            val subFaceFiles = context.assets.list("")
            if (subFaceFiles != null) {
                for (index in subFaceFiles.indices) {
                    //插入照片
                    FaceSearchImagesManger.IL1Iii.getInstance(context)?.insertOrUpdateFaceImage(
                        getBitmapFromAsset(
                            assetManager,
                            subFaceFiles[index]
                        ),
                        CACHE_SEARCH_FACE_DIR + File.separatorChar + subFaceFiles[index]
                    )
                }
            }
        }

    }






}