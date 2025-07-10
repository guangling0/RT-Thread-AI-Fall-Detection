package com.example.jiankong;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class history_list_view extends AppCompatActivity {

    private RecyclerView listrv;

    ArrayList<String> time = new ArrayList<>();
    ArrayList<String> time_sustain = new ArrayList<>();

    private Button button;
    private int total;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history_list_view);
        Toolbar mToolbarTb = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(mToolbarTb);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        SharedPreferences sharedPreferences = getSharedPreferences("fall_history",MODE_PRIVATE);
        total = sharedPreferences.getInt("total",0);
        for (int i = 1;i <= total;i++){
            time.add(sharedPreferences.getString("time_fall"+i,""));
            time_sustain.add(sharedPreferences.getString("time_sustained"+i,""));
        }
        listrv = findViewById(R.id.history_list);
        listrv.addItemDecoration(new DividerItemDecoration(this,DividerItemDecoration.VERTICAL));
        listrv.setLayoutManager(new LinearLayoutManager(this));
        listrv.setAdapter(new Myrecycle());
        button = findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.clear();
                editor.putInt("total", 0);
                editor.apply();
                Toast.makeText(history_list_view.this, "清除成功", Toast.LENGTH_SHORT).show();
            }
        });

    }
    class Myrecycle extends RecyclerView.Adapter<Myrecycle.ViewHolder>{
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = View.inflate(history_list_view.this,R.layout.history_list_item,null);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.time.setText(time.get(position));
            holder.time_sustain.setText(time_sustain.get(position));

        }

        @Override
        public int getItemCount()
        {
            return time.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder{


            TextView time ,time_sustain;
            public ViewHolder(@NonNull View itemView){
                super(itemView);
                time = itemView.findViewById(R.id.fall);
                time_sustain = itemView.findViewById(R.id.fall_sustain);
            }
        }

    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}