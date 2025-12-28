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

/** ListView adapter for rows containing a title and subtitle. */
public class ParamAdapter extends BaseAdapter {
    List<item> items;

    Context context;

    /**
     * Creates an adapter backed by the supplied item list.
     *
     * @param items mutable rows displayed by the adapter
     * @param context context used to inflate row layouts
     */
    public ParamAdapter(List<item> items, Context context) {
        this.items = items;
        this.context = context;
    }

    /** {@inheritDoc} */
    @Override
    public int getCount() {
        return items.size();
    }

    /** {@inheritDoc} */
    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    /**
     * Returns the row position because the model does not define stable IDs.
     *
     * @param position row index
     * @return {@code position}
     */
    @Override
    public long getItemId(int position) {
        return position;
    }

    /**
     * Inflates and binds a title/subtitle row.
     *
     * <p>The current implementation always inflates a new view and does not reuse {@code convertView}.
     *
     * @param position row index
     * @param convertView reusable view supplied by the parent; currently ignored
     * @param parent parent requesting the row
     * @return bound row view
     */
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        @SuppressLint({"ViewHolder", "InflateParams"})
        View view = LayoutInflater.from(context).inflate(R.layout.param_list_item, null);

        TextView title = view.findViewById(R.id.title);
        TextView subtitle = view.findViewById(R.id.subtitle);

        title.setText(items.get(position).title);
        subtitle.setText(items.get(position).subtitle);

        return view;
    }

    /** Mutable title/subtitle model shared by the list and RecyclerView adapters. */
    public static class item {
        /** Primary row text. */
        public String title;
        /** Optional secondary row text. */
        public String subtitle;
    }
}
