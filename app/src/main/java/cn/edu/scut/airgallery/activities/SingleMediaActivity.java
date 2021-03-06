package cn.edu.scut.airgallery.activities;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.edu.scut.airgallery.R;
import cn.edu.scut.airgallery.adapters.MediaPagerAdapter;
import cn.edu.scut.airgallery.data.Album;
import cn.edu.scut.airgallery.data.Media;
import cn.edu.scut.airgallery.data.MediaHelper;
import cn.edu.scut.airgallery.data.StorageHelper;
import cn.edu.scut.airgallery.fragments.BaseMediaFragment;
import cn.edu.scut.airgallery.util.AlertDialogsHelper;
import cn.edu.scut.airgallery.util.Measure;
import cn.edu.scut.airgallery.util.StringUtils;
import cn.edu.scut.airgallery.views.HackyViewPager;
import cn.hzw.doodle.DoodleActivity;
import cn.hzw.doodle.DoodleParams;
import cn.hzw.doodle.DoodleView;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SingleMediaActivity extends AppCompatActivity implements BaseMediaFragment.MediaTapListener{
    public static final String TAG = "SingleMediaActivity";

    private static final String ISLOCKED_ARG = "isLocked";
    public static final int REQ_CODE_DOODLE = 1000;

    public static final String EXTRA_ARGS_ALBUM = "args_album";
    public static final String EXTRA_ARGS_MEDIA = "args_media";
    public static final String EXTRA_ARGS_POSITION = "args_position";

    @BindView(R.id.photos_pager)
    HackyViewPager mViewPager;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    private boolean fullScreenMode = false;

    CompositeDisposable disposables = new CompositeDisposable();

    private Album album;
    private ArrayList<Media> media;
    private int position;
    private MediaPagerAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_media);
        ButterKnife.bind(this);

        loadAlbum(getIntent());

        if (savedInstanceState != null) {
            mViewPager.setLocked(savedInstanceState.getBoolean(ISLOCKED_ARG, false));
        }

        adapter = new MediaPagerAdapter(getSupportFragmentManager(), media);
        initUi();
    }

    private void loadAlbum(Intent intent) {
        album = intent.getParcelableExtra(EXTRA_ARGS_ALBUM);
        position = intent.getIntExtra(EXTRA_ARGS_POSITION, 0);
        media = intent.getParcelableArrayListExtra(EXTRA_ARGS_MEDIA);
    }

    private void deleteCurrentMedia() {
        Media currentMedia = getCurrentMedia();

        Disposable disposable = MediaHelper.deleteMedia(getApplicationContext(), currentMedia)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(deleted -> {
                            media.remove(deleted);
                            if (media.size() == 0) {
                                displayAlbums();
                            }
                        },
                        err -> {
                            // TODO: 21/05/18 add progress show errors better?
                            Toast.makeText(getApplicationContext(), err.getMessage(), Toast.LENGTH_SHORT).show();
                        },
                        () -> {
                            adapter.notifyDataSetChanged();
                            updatePageTitle(mViewPager.getCurrentItem());
                        });

        disposables.add(disposable);
    }

    private void displayAlbums() {
        startActivity(new Intent(getApplicationContext(), MainActivity.class));
        finish();
    }

    @SuppressLint("ResourceAsColor")
    private void initUi()
    {
        /*
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Window window = this.getWindow();
                window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                window.setStatusBarColor(R.color.md_deep_purple_900);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        setSupportActionBar(toolbar);
        toolbar.bringToFront();
        toolbar.setNavigationIcon(R.drawable.ic_back);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        updatePageTitle(position);

        mViewPager.setAdapter(adapter);
        mViewPager.setCurrentItem(position);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                SingleMediaActivity.this.position = position;
                updatePageTitle(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        if (((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRotation() == Surface.ROTATION_90) {
            Configuration configuration = new Configuration();
            configuration.orientation = Configuration.ORIENTATION_LANDSCAPE;
            onConfigurationChanged(configuration);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_single_media, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_edit:
                Uri mDestinationUri = Uri.fromFile(new File(getCacheDir(), "croppedImage.png"));
                Uri uri = Uri.fromFile(new File(getCurrentMedia().getPath()));
                UCrop uCrop = UCrop.of(uri, mDestinationUri);
                uCrop.withOptions(getUcropOptions());
                uCrop.start(SingleMediaActivity.this);
                break;
            case R.id.action_doodle:
                DoodleParams params = initDoodelParams();
                DoodleActivity.startActivityForResult(SingleMediaActivity.this, params, REQ_CODE_DOODLE);
                break;
            case R.id.action_share:
                Intent intent = new Intent(getApplicationContext(), ShareActivity.class);
                intent.putExtra(ShareActivity.ARGS_MEDIA_PATH, getCurrentMedia().getPath());
                startActivity(intent);
                break;
            case R.id.action_delete:
                final AlertDialog textDialog = AlertDialogsHelper.getTextDialog(SingleMediaActivity.this, R.string.delete, R.string.delete_photo_message);
                textDialog.setButton(DialogInterface.BUTTON_NEGATIVE, this.getString(R.string.cancel).toUpperCase(), (dialogInterface, i) -> textDialog.dismiss());
                textDialog.setButton(DialogInterface.BUTTON_POSITIVE, this.getString(R.string.delete).toUpperCase(), (dialog, id) -> deleteCurrentMedia());
                textDialog.show();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null && resultCode == RESULT_OK) {
            switch (requestCode) {
                case UCrop.REQUEST_CROP:
                    final Uri imageUri = UCrop.getOutput(data);
                    if (imageUri != null && imageUri.getScheme().equals("file")) {
                        try {
                            if (StorageHelper.copyFile(getApplicationContext(), new File(imageUri.getPath()), new File(this.album.getPath()))) {
                                Toast.makeText(this, R.string.new_file_created, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Log.e("ERROS - uCrop", imageUri.toString(), e);
                        }
                    } else
                        StringUtils.showToast(getApplicationContext(), "errori random");
                    break;
                case REQ_CODE_DOODLE:
                    final String imagePath = data.getStringExtra(DoodleActivity.KEY_IMAGE_PATH);
                    if (imagePath != null) {
                        try {
                            if (StorageHelper.copyFile(getApplicationContext(), new File(imagePath), new File(this.album.getPath()))) {
                                Toast.makeText(this, R.string.new_file_created, Toast.LENGTH_SHORT).show();
                                String cachePath = "/storage/emulated/0/DCIM/Doodle";
                                StorageHelper.deleteFilesInFolder(getApplicationContext(),new File(cachePath));
                            }
                        } catch (Exception e) {
                            Log.e("ERROS - Dooldle", imagePath, e);
                        }
                    } else
                        StringUtils.showToast(getApplicationContext(), "errori random");
                    break;
                default:
                    super.onActivityResult(requestCode, resultCode, data);
                    break;
            }
        }
    }

    private UCrop.Options getUcropOptions() {
        UCrop.Options options = new UCrop.Options();
        options.setCompressionFormat(Bitmap.CompressFormat.PNG);
        options.setCompressionQuality(90);
        options.setFreeStyleCropEnabled(true);
        return options;
    }

    public DoodleParams initDoodelParams(){
        DoodleParams params = new DoodleParams();
        params.mImagePath = getCurrentMedia().getPath();
        params.mIsFullScreen = true;
        params.mPaintUnitSize = DoodleView.DEFAULT_SIZE;
        params.mPaintColor = Color.RED;
        params.mSupportScaleItem = true;
        return params;
    }

    private void updatePageTitle(int position) {
        getSupportActionBar().setTitle(getString(R.string.of, position + 1, adapter.getCount()));
    }

    public Media getCurrentMedia() {
        return media.get(position);
    }


    @Override
    public void onViewTapped() {
        toggleSystemUI();
    }

    public void toggleSystemUI() {
        if (fullScreenMode) showSystemUI();
        else hideSystemUI();
    }

    private void hideSystemUI() {
        runOnUiThread(new Runnable() {
            public void run() {
                toolbar.animate().translationY(-toolbar.getHeight()).setInterpolator(new AccelerateInterpolator())
                        .setDuration(200).start();

                getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        Log.wtf(TAG, "ui changed: " + visibility);
                    }
                });
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_IMMERSIVE);

                fullScreenMode = true;
            }
        });
    }

    private void showSystemUI() {
        runOnUiThread(new Runnable() {
            public void run() {
                toolbar.animate().translationY(0).setInterpolator(new DecelerateInterpolator())
                        .setDuration(240).start();

                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

                fullScreenMode = false;
            }
        });
    }
}
