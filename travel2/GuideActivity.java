package com.example.travel2;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class GuideActivity extends AppCompatActivity {

    private TextToSpeech tts;              // TTS 변수 선언
    private Button button01, button02, button03;
    private TextView text_op, text_guide, text_weather;

    private final String TTS_ID = "TTS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_guide);

        // XML에서 뷰 참조
        button01 = findViewById(R.id.button1);
        button02 = findViewById(R.id.button2);
        button03 = findViewById(R.id.button3);
        text_op = findViewById(R.id.text_op);
        text_guide = findViewById(R.id.text_guide);
        text_weather = findViewById(R.id.text_weather);

        // TTS 초기화
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.KOREAN);
                }
            }
        });

        // 버튼 클릭 이벤트 처리
        button01.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tts.speak(text_op.getText(), TextToSpeech.QUEUE_FLUSH, null, TTS_ID);
            }
        });

        button02.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tts.speak(text_guide.getText(), TextToSpeech.QUEUE_FLUSH, null, TTS_ID);
            }
        });

        button03.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tts.speak(text_weather.getText(), TextToSpeech.QUEUE_FLUSH, null, TTS_ID);
            }
        });

        // 화면 크기 처리
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main4), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // TTS 객체가 남아있다면 실행을 중지하고 메모리에서 제거한다.
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    public void onButtonClick(View view) {
        if (view.getId() == R.id.button1) {
            tts.speak(text_op.getText(), TextToSpeech.QUEUE_FLUSH, null, TTS_ID);
        } else if (view.getId() == R.id.button2) {
            tts.speak(text_guide.getText(), TextToSpeech.QUEUE_FLUSH, null, TTS_ID);
        } else if (view.getId() == R.id.button3) {
            tts.speak(text_weather.getText(), TextToSpeech.QUEUE_FLUSH, null, TTS_ID);
        }
    }

    public void onClick_back(View view) {
        Intent intent = new Intent(GuideActivity.this, MainActivity.class);
        startActivity(intent);
    }
} 