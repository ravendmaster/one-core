package com.ravendmaster.onecore;

import android.support.v4.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ravendmaster.onecore.activity.TabsActivity;
import com.woxthebox.draglistview.DragItemAdapter;

import java.util.ArrayList;

public class TabItemAdapter extends DragItemAdapter<Pair<Long, TabData>, TabItemAdapter.ViewHolder> {

    private int mLayoutId;
    private int mGrabHandleId;

    public TabItemAdapter(ArrayList<Pair<Long, TabData>> list, int layoutId, int grabHandleId, boolean dragOnLongPress) {
        super(dragOnLongPress);
        mLayoutId = layoutId;
        mGrabHandleId = grabHandleId;
        setHasStableIds(true);
        setItemList(list);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(mLayoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        super.onBindViewHolder(holder, position);
        if(holder.mTabName==null)return;

        TabData tab = mItemList.get(position).second;

        //Presenter presenter= MainActivity.presenter;
        holder.mTabEditButton.setTag(tab);

        holder.mTabName.setText(tab.getName());

    }

    @Override
    public long getItemId(int position) {
        return mItemList.get(position).first;
    }

    public class ViewHolder extends DragItemAdapter<Pair<Long, TabData>, TabItemAdapter.ViewHolder>.ViewHolder {
        public TextView mTabName;
        ImageView mTabEditButton;

        public ViewHolder(final View itemView) {
            super(itemView, mGrabHandleId);
            mTabName = (TextView) itemView.findViewById(R.id.tab_name);

            mTabEditButton = (ImageView) itemView.findViewById(R.id.imageView_edit_button);


            mTabEditButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //MainActivity.instance.showPopupMenuWidgetEditButtonOnClick(view);
                    TabsActivity.Companion.getInstance().showPopupMenuTabEditButtonOnClick(view);
                    //Toast.makeText(view.getContext(), "Item edit pressed", Toast.LENGTH_SHORT).show();
                }
            });

        }


        @Override
        public void onItemClicked(View view) {
            //Toast.makeText(view.getContext(), "Item clicked", Toast.LENGTH_SHORT).show();
        }

        @Override
        public boolean onItemLongClicked(View view) {
            //Toast.makeText(view.getContext(), "Item long clicked", Toast.LENGTH_SHORT).show();
            return true;
        }
    }
}
