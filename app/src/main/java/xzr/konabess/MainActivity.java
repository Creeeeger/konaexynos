package xzr.konabess;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.ArrayList;

import xzr.konabess.adapters.ParamAdapter;
import xzr.konabess.utils.DialogUtil;

public class MainActivity extends AppCompatActivity {
    private static Thread permission_worker;
    private static fileWorker file_worker;
    AlertDialog waiting;
    boolean cross_device_debug = false;
    onBackPressedListener onBackPressedListener = null;
    LinearLayout mainView;
    LinearLayout showdView;

    public static void runWithStoragePermission(AppCompatActivity activity, Thread what) {
        MainActivity.permission_worker = what;
        if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
        } else {
            what.start();
            permission_worker = null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ChipInfo.which = ChipInfo.type.unknown;

        try {
            setTitle(getTitle() + " " + getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        try {
            if (!cross_device_debug)
                KonaBessCore.cleanEnv(this);
            KonaBessCore.setupEnv(this);
        } catch (Exception e) {
            DialogUtil.showError(this, R.string.environ_setup_failed);
            return;
        }

        new unpackLogic().start();
    }

    @Override
    public void onBackPressed() {
        if (onBackPressedListener != null)
            onBackPressedListener.onBackPressed();
        else
            super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            file_worker.uri = data.getData();
            if (file_worker != null) {
                file_worker.start();
                file_worker = null;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (permission_worker != null) {
                permission_worker.start();
                permission_worker = null;
            }
        } else {
            Toast.makeText(this, R.string.storage_permission_failed, Toast.LENGTH_SHORT).show();
        }
    }

    void showMainView() {
        onBackPressedListener = null;
        mainView = new LinearLayout(this);
        mainView.setOrientation(LinearLayout.VERTICAL);
        setContentView(mainView);

        LinearLayout toolbar = new LinearLayout(this);
        HorizontalScrollView toolbarScroll = new HorizontalScrollView(this);
        toolbarScroll.addView(toolbar);
        mainView.addView(toolbarScroll);

        LinearLayout editor = new LinearLayout(this);
        HorizontalScrollView editorScroll = new HorizontalScrollView(this);
        editorScroll.addView(editor);
        mainView.addView(editorScroll);

        showdView = new LinearLayout(this);
        showdView.setOrientation(LinearLayout.VERTICAL);
        mainView.addView(showdView);

        {
            Button button = new Button(this);
            button.setText(R.string.repack_and_flash);
            toolbar.addView(button);
            button.setOnClickListener(v -> new repackLogic().start());
        }
        {
            Button button = new Button(this);
            button.setText(R.string.backup_old_image);
            toolbar.addView(button);
            button.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle(R.string.backup_old_image)
                    .setMessage(getResources().getString(R.string.will_backup_to) + " /storage/emulated/0/dtb.img")
                    .setPositiveButton(R.string.ok, (dialog, which) -> runWithStoragePermission(this, new backupBoot(this)))
                    .setNegativeButton(R.string.cancel, null)
                    .create().show());
        }
        {
            Button button = new Button(this);
            button.setText(R.string.edit_gpu_freq_table);
            editor.addView(button);
            button.setOnClickListener(v -> new GpuTableEditor.gpuTableLogic(this, showdView).start());
        }
    }

    static class fileWorker extends Thread {
        public Uri uri;
    }

    public static abstract class onBackPressedListener {
        public abstract void onBackPressed();
    }

    class backupBoot extends Thread {
        AppCompatActivity activity;
        AlertDialog waiting;
        boolean is_err;

        public backupBoot(AppCompatActivity activity) {
            this.activity = activity;
        }

        public void run() {
            is_err = false;
            runOnUiThread(() -> {
                waiting = DialogUtil.getWaitDialog(activity, R.string.backuping_img);
                waiting.show();
            });
            try {
                KonaBessCore.backupDtbImage(activity);
            } catch (Exception e) {
                is_err = true;
            }
            runOnUiThread(() -> {
                waiting.dismiss();
                if (is_err)
                    DialogUtil.showError(activity, R.string.failed_backup);
                else
                    Toast.makeText(activity, R.string.backup_success, Toast.LENGTH_SHORT).show();
            });

        }
    }

    class repackLogic extends Thread {
        boolean is_err;
        String error = "";

        public void run() {
            is_err = false;
            {
                runOnUiThread(() -> {
                    waiting = DialogUtil.getWaitDialog(MainActivity.this, R.string.repacking);
                    waiting.show();
                });

                try {
                    KonaBessCore.dts2bootImage(MainActivity.this);
                } catch (Exception e) {
                    is_err = true;
                    error = e.getMessage();
                }
                runOnUiThread(() -> {
                    waiting.dismiss();
                    if (is_err)
                        DialogUtil.showDetailedError(MainActivity.this, R.string.repack_failed, error);
                });
                if (is_err)
                    return;
            }

            if (!cross_device_debug) {
                runOnUiThread(() -> {
                    waiting = DialogUtil.getWaitDialog(MainActivity.this, R.string.flashing_boot);
                    waiting.show();
                });

                try {
                    KonaBessCore.writeDtbImage(MainActivity.this);
                } catch (Exception e) {
                    is_err = true;
                }
                runOnUiThread(() -> {
                    waiting.dismiss();
                    if (is_err)
                        DialogUtil.showError(MainActivity.this, R.string.flashing_failed);
                    else {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(R.string.reboot_complete_title)
                                .setMessage(R.string.reboot_complete_msg)
                                .setPositiveButton(R.string.yes, (dialog, which) -> {
                                    try {
                                        KonaBessCore.reboot();
                                    } catch (IOException e) {
                                        DialogUtil.showError(MainActivity.this,
                                                R.string.failed_reboot);
                                    }
                                })
                                .setNegativeButton(R.string.no, null)
                                .create().show();
                    }
                });
            }
        }
    }

    class unpackLogic extends Thread {
        String error = "";
        boolean is_err;
        int dtb_index;

        public void run() {
            is_err = false;
            {
                runOnUiThread(() -> {
                    waiting = DialogUtil.getWaitDialog(MainActivity.this, R.string.getting_image);
                    waiting.show();
                });
                try {
                    if (!cross_device_debug)
                        KonaBessCore.getDtImage(MainActivity.this);
                } catch (Exception e) {
                    is_err = true;
                }
                runOnUiThread(() -> {
                    waiting.dismiss();
                    if (is_err)
                        DialogUtil.showError(MainActivity.this, R.string.failed_get_boot);
                });
                if (is_err)
                    return;
            }

            {
                runOnUiThread(() -> {
                    waiting = DialogUtil.getWaitDialog(MainActivity.this, R.string.unpacking);
                    waiting.show();
                });
                try {
                    KonaBessCore.dtbImage2dts(MainActivity.this);
                } catch (Exception e) {
                    is_err = true;
                    error = e.getMessage();
                }
                runOnUiThread(() -> {
                    waiting.dismiss();
                    if (is_err)
                        DialogUtil.showDetailedError(MainActivity.this, R.string.unpack_failed, error);
                });
                if (is_err)
                    return;
            }

            {
                runOnUiThread(() -> {
                    waiting = DialogUtil.getWaitDialog(MainActivity.this, R.string.checking_device);
                    waiting.show();
                });
                try {
                    KonaBessCore.checkDevice(MainActivity.this);
                    dtb_index = KonaBessCore.getDtbIndex();
                } catch (Exception e) {
                    is_err = true;
                    error = e.getMessage();
                }
                runOnUiThread(() -> {
                    waiting.dismiss();
                    if (is_err)
                        DialogUtil.showDetailedError(MainActivity.this,
                                R.string.failed_checking_platform, error);
                });
                if (is_err)
                    return;
            }

            runOnUiThread(() -> {
                if (KonaBessCore.dtbs.size() == 0) {
                    DialogUtil.showError(MainActivity.this, R.string.incompatible_device);
                    return;
                }
                if (KonaBessCore.dtbs.size() == 1) {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(0), MainActivity.this);
                    showMainView();
                    return;
                }
                ListView listView = new ListView(MainActivity.this);
                ArrayList<ParamAdapter.item> items = new ArrayList<>();
                for (KonaBessCore.dtb dtb : KonaBessCore.dtbs) {
                    items.add(new ParamAdapter.item() {{
                        title = dtb.id + " " + ChipInfo.name2ChipDesc(dtb.type, MainActivity.this);
                        subtitle = dtb.id == dtb_index ?
                                MainActivity.this.getString(R.string.possible_dtb) : "";
                    }});
                }
                listView.setAdapter(new ParamAdapter(items, MainActivity.this));
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.select_dtb_title)
                        .setMessage(R.string.select_dtb_msg)
                        .setView(listView)
                        .setCancelable(false)
                        .create();
                dialog.show();

                listView.setOnItemClickListener((parent, view, position, id) -> {
                    KonaBessCore.chooseTarget(KonaBessCore.dtbs.get(position), MainActivity.this);
                    dialog.dismiss();
                    showMainView();
                });
            });
        }
    }
}