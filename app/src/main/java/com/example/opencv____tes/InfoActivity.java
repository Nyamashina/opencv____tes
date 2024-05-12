package com.example.opencv____tes;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

public class InfoActivity extends Activity {
    String TAG = "InfoActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // activity_info.xmlレイアウトをこのアクティビティのレイアウトとして設定
        setContentView(R.layout.activity_info);

        // レイアウト内のコンポーネントにアクセス
        Button backButton = findViewById(R.id.back_button);

        // 必要に応じてコンポーネントの設定を変更
        // 例: ボタンにクリックリスナーを設定
        backButton.setOnClickListener(v -> {
            Log.d(TAG,"info end");
            finish(); // このアクティビティを終了して前の画面に戻る
        });
    }
}
