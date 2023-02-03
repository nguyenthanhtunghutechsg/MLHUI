package MLHUI_Miner;

import java.util.ArrayList;
/* This file is copyright (c) 2008-2013 Philippe Fournier-Viger
* 
* This file is part of the SPMF DATA MINING SOFTWARE
* (http://www.philippe-fournier-viger.com/spmf).
* 
* SPMF is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* SPMF is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* SPMF. If not, see <http://www.gnu.org/licenses/>.
 */
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * This class represents a UtilityList as used by the HUI-Miner algorithm.
 *
 * @see AlgoHUIMiner
 * @see Element
 * @author Philippe Fournier-Viger
 */
public class UtilityList {

    int[] nameOfItemset;
    double sumIutils = 0;  // the sum of item utilities
    double sumRutils = 0;  // the sum of remaining utilities
    Map<Integer, Element> elements = new HashMap<>();  // the elements

    public String getName() {
    	String name ="";
    	for (int i : nameOfItemset) {
    		name+=i+" ";
		}
    	return name;
    }
    public ArrayList<Integer> getParent(Taxonomy taxonomy) {
    	HashSet<Integer> hashParent = new HashSet<Integer>();
    	ArrayList<Integer> arrayList = new ArrayList<Integer>();
    	for (Integer i : nameOfItemset) {
    		hashParent.add(taxonomy.MapdataParent.get(i));
		}
    	for (Integer i : hashParent) {
    		if (i!=null) {
    			arrayList.add(i);
			}
		}
    	return arrayList;
    }
    
    /**
     * Constructor.
     *
     * @param oldItemset
     * @param item the item that is used for this utility list
     */
    public UtilityList(int oldItemset[] ,Integer item) {
        nameOfItemset = new int[oldItemset.length+1];
        System.arraycopy(oldItemset, 0, nameOfItemset, 0, oldItemset.length);
        nameOfItemset[oldItemset.length] = item;
    }
    public UtilityList(int oldItemset[]) {
        nameOfItemset = new int[oldItemset.length];
        System.arraycopy(oldItemset, 0, nameOfItemset, 0, oldItemset.length);
    }

    /**
     * Method to add an element to this utility list and update the sums at the
     * same time.
     * @param TID
     * @param element
     */
    public void addElement(int TID,Element element) {
        sumIutils += element.iutils;
        sumRutils += element.rutils;
        elements.put(TID,element);
    }

    /**
     * Get the support of the itemset represented by this utility-list
     *
     * @return the support as a number of trnsactions
     */
    public int getSupport() {
        return elements.size();
    }
}
