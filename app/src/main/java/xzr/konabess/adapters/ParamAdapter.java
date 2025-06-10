package xzr.konabess.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import xzr.konabess.R;

// Adapter class to provide views for a ListView based on a list of item objects
public class ParamAdapter extends BaseAdapter {
    // List of items to display in the ListView
    List<item> items;
    // Application context, used for inflating layouts
    Context context;

    // Constructor to initialize adapter with data and context
    public ParamAdapter(List<item> items, Context context) {
        this.items = items;
        this.context = context;
    }

    // Returns the total number of items in the list
    @Override
    public int getCount() {
        return items.size();
    }

    // Returns the item at the specified position
    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    // Returns the item ID (here, just the position in the list)
    @Override
    public long getItemId(int position) {
        return position;
    }

    // Provides a view for an adapter view (ListView)
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Disable ViewHolder optimization and always inflate a new view (not recommended for large lists)
        @SuppressLint({"ViewHolder", "InflateParams"})
        View view = LayoutInflater.from(context).inflate(R.layout.param_list_item, null);

        // Find the title and subtitle TextViews in the inflated layout
        TextView title = view.findViewById(R.id.title);
        TextView subtitle = view.findViewById(R.id.subtitle);

        // Set text for title and subtitle based on the item at the current position
        title.setText(items.get(position).title);
        subtitle.setText(items.get(position).subtitle);

        return view;
    }

    // Static inner class representing a single data item with a title and subtitle
    public static class item {
        public String title;
        public String subtitle;
    }
}