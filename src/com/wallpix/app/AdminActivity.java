package com.wallpix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Admin console: import wallpapers, feature / rename / delete them, change the PIN. */
public class AdminActivity extends Activity {

    private static final int REQ_ONE = 1001;
    private static final int REQ_MANY = 1002;
    private static final String[] CATS =
            {"Nature", "Anime", "Abstract", "Cars", "Space", "Minimal", "Other"};

    private SharedPreferences prefs;
    private final List<Wallpaper> items = new ArrayList<>();
    private WallAdapter adapter;
    private GridView grid;
    private TextView stats, empty, pinError, pinHint;
    private LinearLayout pinBox, adminBox;
    private EditText pinInput;
    private boolean unlocked, picking, internalNav;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_admin);
        prefs = getSharedPreferences(Store.PREFS, MODE_PRIVATE);

        pinBox = findViewById(R.id.pinBox);
        adminBox = findViewById(R.id.adminBox);
        pinInput = findViewById(R.id.pinInput);
        pinError = findViewById(R.id.pinError);
        pinHint = findViewById(R.id.pinHint);
        stats = findViewById(R.id.stats);
        empty = findViewById(R.id.empty);
        grid = findViewById(R.id.grid);

        if (!Store.DEFAULT_PIN.equals(Store.getPin(this))) {
            pinHint.setVisibility(View.GONE);
        }

        adapter = new WallAdapter(this);
        adapter.setListener(this::showItemMenu);
        grid.setAdapter(adapter);
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            showItemMenu(adapter.getItem(pos));
            return true;
        });

        findViewById(R.id.unlockBtn).setOnClickListener(v -> tryUnlock());
        pinInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (s.length() == 4) tryUnlock();
            }
        });

        findViewById(R.id.addBtn).setOnClickListener(v -> pick(false));
        findViewById(R.id.bulkBtn).setOnClickListener(v -> pick(true));
        findViewById(R.id.lockBtn).setOnClickListener(v -> lockNow());
        findViewById(R.id.moreBtn).setOnClickListener(this::showMore);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (unlocked) showConsole(); else showPin();
        internalNav = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isChangingConfigurations() && !picking && !internalNav) {
            unlocked = false;
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        picking = false;
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_ONE) {
            Uri u = data.getData();
            if (u != null) showAddDialog(u, displayName(u));
        } else if (req == REQ_MANY) {
            ArrayList<Uri> uris = new ArrayList<>();
            ClipData cd = data.getClipData();
            if (cd != null) {
                for (int i = 0; i < cd.getItemCount(); i++) uris.add(cd.getItemAt(i).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) bulkImport(uris);
        }
    }

    // ------------------------------------------------------------ PIN

    private void showPin() {
        pinBox.setVisibility(View.VISIBLE);
        adminBox.setVisibility(View.GONE);
        pinError.setVisibility(View.GONE);
    }

    private void showConsole() {
        pinBox.setVisibility(View.GONE);
        adminBox.setVisibility(View.VISIBLE);
        hideKeyboard();
        load();
    }

    private void tryUnlock() {
        String pin = pinInput.getText().toString();
        if (pin.equals(Store.getPin(this))) {
            unlocked = true;
            pinInput.setText("");
            showConsole();
        } else if (pin.length() >= 4) {
            pinError.setVisibility(View.VISIBLE);
            pinInput.setText("");
        }
    }

    private void lockNow() {
        unlocked = false;
        pinInput.setText("");
        pinError.setVisibility(View.GONE);
        showPin();
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(pinInput.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------ DATA / GRID

    private void load() {
        items.clear();
        items.addAll(Store.all(this));
        adapter.setItems(items);

        int n = items.size();
        Set<String> cats = new HashSet<>();
        for (Wallpaper w : items) cats.add(w.category);
        stats.setText(n + (n == 1 ? " wallpaper" : " wallpapers")
                + " • " + cats.size() + (cats.size() == 1 ? " category" : " categories"));
        if (Cloud.enabled(this)) stats.append(" • cloud ✓");

        boolean none = n == 0;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        grid.setVisibility(none ? View.GONE : View.VISIBLE);
    }

    // --------------------------------------------------------- IMPORT

    private void pick(boolean many) {
        picking = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (many) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, many ? REQ_MANY : REQ_ONE);
        } catch (Exception e) {
            picking = false;
            Ui.toast(this, "No gallery app found");
        }
    }

    private void showAddDialog(Uri uri, String suggestedTitle) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add, null);
        RoundImageView preview = v.findViewById(R.id.preview);
        EditText titleInput = v.findViewById(R.id.titleInput);
        LinearLayout catChips = v.findViewById(R.id.catChips);

        try { preview.setImageURI(uri); } catch (Exception ignored) {}
        titleInput.setText(suggestedTitle);
        titleInput.setSelection(titleInput.getText().length());

        final String[] selected = {"Nature"};
        final List<TextView> chipViews = new ArrayList<>();
        for (String cat : CATS) {
            final TextView chip = Ui.chip(this, cat);
            chip.setSelected(cat.equals(selected[0]));
            chip.setOnClickListener(view -> {
                selected[0] = cat;
                for (TextView t : chipViews) t.setSelected(t == chip);
            });
            chipViews.add(chip);
            catChips.addView(chip);
        }

        AlertDialog d = new AlertDialog.Builder(this).setView(v).create();
        Window win = d.getWindow();
        if (win != null) win.setBackgroundDrawableResource(android.R.color.transparent);
        d.show();
        if (win != null) {
            win.setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }

        v.findViewById(R.id.cancelBtn).setOnClickListener(x -> d.dismiss());
        v.findViewById(R.id.saveBtn).setOnClickListener(x -> {
            String title = titleInput.getText().toString().trim();
            String cat = selected[0];
            d.dismiss();
            importOne(uri, title, cat, suggestedTitle);
        });
    }

    private void importOne(Uri uri, String title, String category, String fallback) {
        ProgressDialog pd = Ui.progress(this, "Adding wallpaper…");
        final boolean cloudOn = Cloud.enabled(this);
        new Thread(() -> {
            try {
                Wallpaper w = Store.add(this, uri, title, category, fallback);
                String cloudErr = null;
                if (cloudOn) {
                    try {
                        Cloud.upload(this, w, Store.imageFile(this, w), Store.thumbFile(this, w));
                    } catch (Exception ce) {
                        cloudErr = ce.getMessage();
                    }
                }
                final String ce = cloudErr;
                ui.post(() -> {
                    pd.dismiss();
                    if (!cloudOn) Ui.toast(this, "Wallpaper added ✓");
                    else if (ce == null) Ui.toast(this, "Added & synced ✓");
                    else Ui.toast(this, "Added locally • cloud: " + ce);
                    load();
                });
            } catch (Exception e) {
                ui.post(() -> {
                    pd.dismiss();
                    Ui.toast(this, "Failed: " + e.getMessage());
                });
            }
        }).start();
    }

    private void bulkImport(List<Uri> uris) {
        ProgressDialog pd = Ui.progress(this, "Adding " + uris.size() + " wallpapers…");
        final boolean cloudOn = Cloud.enabled(this);
        new Thread(() -> {
            int ok = 0, synced = 0;
            for (Uri u : uris) {
                String name = displayName(u);
                try {
                    Wallpaper w = Store.add(this, u, name, "Other", name);
                    ok++;
                    if (cloudOn) {
                        try {
                            Cloud.upload(this, w, Store.imageFile(this, w), Store.thumbFile(this, w));
                            synced++;
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
            final int done = ok, up = synced;
            ui.post(() -> {
                pd.dismiss();
                String msg = done + " wallpaper" + (done == 1 ? "" : "s") + " added";
                if (cloudOn) msg += " • " + up + " synced";
                Ui.toast(this, msg + " ✓");
                load();
            });
        }).start();
    }

    private String displayName(Uri uri) {
        String name = null;
        try {
            Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (idx >= 0) name = c.getString(idx);
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {}
        if (name == null) {
            name = uri.getLastPathSegment();
            if (name == null) name = "Wallpaper";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replace('_', ' ').trim();
    }

    // ------------------------------------------------- ITEM ACTIONS

    private void showItemMenu(Wallpaper w) {
        String[] options = {
                "Open preview",
                w.featured ? "Remove from featured" : "Mark as featured",
                "Rename",
                "Delete"
        };
        new AlertDialog.Builder(this)
                .setTitle(w.title)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            openPreview(w);
                            break;
                        case 1:
                            w.featured = !w.featured;
                            Store.update(this, w);
                            syncMeta(w);
                            load();
                            Ui.toast(this, w.featured ? "Marked as featured" : "Featured removed");
                            break;
                        case 2:
                            renameDialog(w);
                            break;
                        default:
                            deleteDialog(w);
                    }
                })
                .show();
    }

    private void openPreview(Wallpaper w) {
        internalNav = true;
        Intent i = new Intent(this, PreviewActivity.class);
        i.putExtra(PreviewActivity.EXTRA_ID, w.id);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void renameDialog(Wallpaper w) {
        EditText e = new EditText(this);
        e.setText(w.title);
        e.setSelection(e.getText().length());
        e.setSingleLine(true);
        e.setBackgroundResource(R.drawable.input_bg);
        e.setTextColor(getResources().getColor(R.color.text, getTheme()));
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));

        FrameLayout box = new FrameLayout(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("Rename wallpaper")
                .setView(box)
                .setPositiveButton("Save", (d, i) -> {
                    String t = e.getText().toString().trim();
                    if (!t.isEmpty()) {
                        w.title = t;
                        Store.update(this, w);
                        syncMeta(w);
                        load();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteDialog(Wallpaper w) {
        new AlertDialog.Builder(this)
                .setTitle("Delete wallpaper?")
                .setMessage(w.title)
                .setPositiveButton("Delete", (d, i) -> {
                    Store.delete(this, w);
                    syncDelete(w);
                    load();
                    Ui.toast(this, "Deleted");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------- OVERFLOW

    private void showMore(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add("Open User Panel");
        pm.getMenu().add("Cloud sync settings");
        pm.getMenu().add("Push all to cloud");
        pm.getMenu().add("Change PIN");
        pm.getMenu().add("Delete all wallpapers");
        pm.setOnMenuItemClickListener(item -> {
            String title = String.valueOf(item.getTitle());
            if (title.startsWith("Open")) {
                internalNav = true;
                startActivity(new Intent(this, UserActivity.class));
            } else if (title.startsWith("Cloud")) {
                Cloud.showSettings(this, true, null);
            } else if (title.startsWith("Push")) {
                pushAll();
            } else if (title.startsWith("Change")) {
                changePinDialog();
            } else {
                deleteAllDialog();
            }
            return true;
        });
        pm.show();
    }

    private void pushAll() {
        final List<Wallpaper> all = new ArrayList<>(Store.all(this));
        if (!Cloud.enabled(this)) {
            Ui.toast(this, "Pehle cloud server URL set karo");
            Cloud.showSettings(this, true, null);
            return;
        }
        if (all.isEmpty()) {
            Ui.toast(this, "Koi local wallpaper nahi hai");
            return;
        }

        ProgressDialog pd = Ui.progress(this, "Uploading " + all.size() + " wallpapers…");
        new Thread(() -> {
            int ok = 0;
            String lastError = null;
            for (Wallpaper w : all) {
                try {
                    Cloud.upload(this, w, Store.imageFile(this, w), Store.thumbFile(this, w));
                    ok++;
                } catch (Exception e) {
                    lastError = e.getMessage();
                }
            }
            final int done = ok;
            final String err = lastError;
            ui.post(() -> {
                pd.dismiss();
                String msg = done + " wallpaper" + (done == 1 ? "" : "s") + " uploaded";
                if (err != null) msg += " • error: " + err;
                Ui.toast(this, msg);
            });
        }).start();
    }

    private void syncMeta(Wallpaper w) {
        if (!Cloud.enabled(this)) return;
        new Thread(() -> {
            try { Cloud.updateMeta(this, w); } catch (Exception ignored) {}
        }).start();
    }

    private void syncDelete(Wallpaper w) {
        if (!Cloud.enabled(this)) return;
        new Thread(() -> {
            try { Cloud.remove(this, w.id); } catch (Exception ignored) {}
        }).start();
    }

    private void changePinDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);

        EditText oldPin = pinField("Current PIN");
        EditText newPin = pinField("New 4-digit PIN");
        EditText confirm = pinField("Confirm new PIN");
        box.addView(oldPin);
        box.addView(newPin);
        box.addView(confirm);

        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("Change PIN")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String cur = oldPin.getText().toString();
            String np = newPin.getText().toString();
            String cp = confirm.getText().toString();
            if (!cur.equals(Store.getPin(this))) {
                Ui.toast(this, "Current PIN is wrong");
            } else if (np.length() != 4) {
                Ui.toast(this, "New PIN must be 4 digits");
            } else if (!np.equals(cp)) {
                Ui.toast(this, "PINs do not match");
            } else {
                Store.setPin(this, np);
                pinHint.setVisibility(View.GONE);
                d.dismiss();
                Ui.toast(this, "PIN updated ✓");
            }
        }));
        d.show();
    }

    private EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setSingleLine(true);
        e.setMaxLines(1);
        e.setBackgroundResource(R.drawable.input_bg);
        e.setTextColor(getResources().getColor(R.color.text, getTheme()));
        e.setHintTextColor(getResources().getColor(R.color.muted2, getTheme()));
        e.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 10);
        e.setLayoutParams(lp);
        return e;
    }

    private void deleteAllDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all wallpapers?")
                .setMessage("This removes every wallpaper from the user panel too.")
                .setPositiveButton("Delete all", (d, i) -> {
                    List<Wallpaper> all = new ArrayList<>(Store.all(this));
                    for (Wallpaper w : all) {
                        Store.delete(this, w);
                    }
                    if (Cloud.enabled(this)) {
                        new Thread(() -> {
                            for (Wallpaper w : all) {
                                try { Cloud.remove(this, w.id); } catch (Exception ignored) {}
                            }
                        }).start();
                    }
                    load();
                    Ui.toast(this, "All wallpapers deleted");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
