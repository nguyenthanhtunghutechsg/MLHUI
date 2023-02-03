package MLHUI_Miner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

// ML-HUI-MINER ALGORITHM MULTI-CORE
// ---------------------------------
// This algorithm extends the FHM algorithm to mine multi-level high-utility itemsets
// 
// Coded by Trinh D.D. Nguyen
// Version 1.1 - Nov, 2020
//
public class AlgoMLHUIMinerMC3 {

    public long timerStart = 0; // time stamps for benchmarking purpose
    public long timerStop = 0;
    public long patternCount = 0; // multi-level HUIs found
    public long candidateCount = 0; // candidate high-utility itemsets counter
    public int transCount = 0; // number of transactions
    public double minUtil = 0.0; // minimum utility
    public List<HashSet<Integer>> itemPromissingEachLevel;

    Map<Integer, Integer> mapItemToLevel;
    Map<Integer, Double> mapItemToTWU; // Map to remember the TWU of each item
    Map<Integer, List<Integer>> mapItemToAncestor;
    Map<Integer, List<Integer>> mapItemToChildren;
    Map<String, UtilityList> mapItemToUtilityList;
    Map<Integer, Map<Integer, Double>> mapFMAP; // EUCS: key:item key:another item value:twu
    int maxLvl;
    Taxonomy taxonomy; // for describing the taxonomy of a dataset

    class Pair { // represents an item and its utility in a transaction

        int item = 0;
        double utility = 0.0;
    }

    // constructor
    public AlgoMLHUIMinerMC3() {
    }

    // main ML-HUI-Miner MC algorithm
    public void runAlgorithm(String inputTransaction, String inputTaxonomy, String output, Double minUtility)
            throws IOException {

        // initializations
        MemoryLogger.getInstance().reset();
        mapFMAP = new HashMap<>();
        mapItemToTWU = new HashMap<>();
        mapItemToLevel = new HashMap<>();
        mapItemToAncestor = new HashMap<>();
        mapItemToChildren = new HashMap<>();
        taxonomy = new Taxonomy(inputTaxonomy);
        mapItemToUtilityList = new HashMap<>();
        
        minUtil = minUtility;

        timerStart = System.currentTimeMillis();

        // first dataset scan to calculate the TWU of each item.
        System.out.println("First dataset scan...");

        Dataset dataset = new Dataset(inputTransaction, Integer.MAX_VALUE);
        transCount = dataset.getTransactions().size();

        for (int tid = 0; tid < transCount; tid++) {
            Transaction transaction = dataset.getTransactions().get(tid);
            ArrayList<Integer> ancestantExist = new ArrayList<>();

            for (int i = 0; i < transaction.getItems().length; i++) { // for each item, add the transaction utility to
                // its TWU
                Integer item = transaction.getItems()[i];
                double transactionUtility = transaction.getUtility();
                Double twu = mapItemToTWU.get(item); // get the current TWU of that item

                // add the utility of the item in the current transaction to its twu
                twu = (twu == null) ? transactionUtility : twu + transactionUtility;

                ArrayList<Integer> ancestor = new ArrayList<>();

                ancestor.add(item);
                mapItemToTWU.put(item, twu);
                if (mapItemToAncestor.get(item) == null) {
                    Integer itemCopy = item;
                    while (itemCopy != null) {
                        Integer childItem = itemCopy;
                        Integer parentItem = taxonomy.MapdataParent.get(childItem);
                        if (parentItem != null) {
                            ancestor.add(parentItem);
                            if (!ancestantExist.contains(parentItem)) {
                                ancestantExist.add(parentItem);
                                Double twuParent = mapItemToTWU.get(parentItem);
                                twuParent = (twuParent == null) ? transactionUtility : transactionUtility + twuParent;
                                mapItemToTWU.put(parentItem, twuParent);
                            }
                        }
                        itemCopy = parentItem;
                    }
                    int k = ancestor.size();
                    for (int j = ancestor.size() - 1; j >= 0; j--, k--) {
                        if (mapItemToLevel.get(ancestor.get(j)) == null) {
                            mapItemToLevel.put(ancestor.get(j), k);
                        } else {
                            if (k < mapItemToLevel.get(ancestor.get(j))) {
                                mapItemToLevel.put(ancestor.get(j), k);
                            }

                        }
                    }
                    for (int itemKey = 0; itemKey < ancestor.size(); itemKey++) {
                        List<Integer> itemValue = new ArrayList<>();
                        for (int listValue = itemKey; listValue < ancestor.size(); listValue++) {
                            itemValue.add(ancestor.get(listValue));

                        }
                        mapItemToAncestor.put(ancestor.get(itemKey), itemValue);
                    }
                } else {
                    List<Integer> listAncestorOfItem = mapItemToAncestor.get(item);

                    for (int k = 1; k < listAncestorOfItem.size(); k++) {
                        if (!ancestantExist.contains(listAncestorOfItem.get(k))) {
                            ancestantExist.add(listAncestorOfItem.get(k));
                            Double twuParent = mapItemToTWU.get(listAncestorOfItem.get(k));
                            twuParent = (twuParent == null) ? transaction.getUtility()
                                    : twuParent + transaction.getUtility();
                            mapItemToTWU.put(listAncestorOfItem.get(k), twuParent);
                        }
                    }
                }
            }
        }

        List<List<UtilityList>> listOfUtilityLists = new ArrayList<>(); // A LIST TO STORE THE UTILITY LIST OF ITEMS
        mapItemToUtilityList = new HashMap<>();
        for (Integer item : mapItemToTWU.keySet()) { // For each item

            if (mapItemToTWU.get(item) >= minUtility) { // if the item is promising (TWU >= minutility)
                UtilityList uList = new UtilityList(new int[0],item); // create an empty Utility List that we will fill later.
                mapItemToUtilityList.put(item.toString(), uList); // add the item to the list of high TWU items
            } else {
                List<Integer> listAncestorOfItem = mapItemToAncestor.get(item);
                for (int k = 0; k < listAncestorOfItem.size(); k++) {
                    if (mapItemToTWU.get(listAncestorOfItem.get(k)) >= minUtility) {
                        UtilityList tuList = new UtilityList(new int[0],item);
                        mapItemToUtilityList.put(item.toString(), tuList);
                        break;
                    }
                }

            }
        }

        List<List<List<Pair>>> revisedTransaction = new ArrayList<>();

        for (int i = 0; i < getMaxLevel(mapItemToLevel); i++) {

            List<List<Pair>> revisedTransactionTemp = new ArrayList<>();

            for (int j = 0; j < transCount; j++) {
                List<Pair> rrTemp = new ArrayList<>();
                revisedTransactionTemp.add(rrTemp);
            }

            revisedTransaction.add(revisedTransactionTemp);
        }

        System.out.println("==== DATASET CHARACTERISTICS ====");
        System.out.println(" Transactions: " + transCount);
        System.out.println(" Levels      : " + getMaxLevel(mapItemToLevel));
        System.out.println(" |GI|        : " + taxonomy.parentCount());
        System.out.println("=================================");

        System.out.println("Second dataset scan...");
        int maxLevel = getMaxLevel(mapItemToLevel);
        maxLvl = maxLevel;
        itemPromissingEachLevel = new ArrayList<>();
        
        for (int tid = 0; tid < transCount; tid++) {

            Transaction transaction = dataset.getTransactions().get(tid);
            int[] items = transaction.getItems();
            double[] remainingUtility = new double[maxLevel];
            double[] newTWU = new double[maxLevel];
            Map<Integer, Double> mapItemToUtility = new HashMap<>();
            double[] utilities = transaction.getUtilities();
            for (int i = 0; i < items.length; i++) {

                Integer item = transaction.getItems()[i];
                mapItemToUtility.put(item, utilities[i]);
                List<Integer> listParent = mapItemToAncestor.get(item);

                for (int k = 1; k < listParent.size(); k++) {
                    int parentItem = listParent.get(k);
                    Double UtilityOfParent = mapItemToUtility.get(parentItem);
                    if (UtilityOfParent == null) {
                        UtilityOfParent = utilities[i];
                    } else {
                        UtilityOfParent += utilities[i];
                    }
                    mapItemToUtility.put(parentItem, UtilityOfParent);
                }

            }
            for (int j : mapItemToUtility.keySet()) {
                int level = mapItemToLevel.get(j);
                if (mapItemToTWU.get(j) > minUtil) {
                    Pair pair = new Pair();
                    pair.item = j;
                    pair.utility = mapItemToUtility.get(j);
                    revisedTransaction.get(level - 1).get(tid).add(pair);
                    remainingUtility[level - 1] += pair.utility;
                    newTWU[level - 1] += pair.utility;
                } else {

                }
            }

            // sort the transaction
            for (int i = 0; i < /* getMaxLevel(mapItemToLevel) */ maxLevel; i++) {
                Collections.sort(revisedTransaction.get(i).get(tid), (Pair o1, Pair o2) -> compareItems(o1.item, o2.item));
            }
            for (int levels =  maxLevel - 1; levels >= 0; levels--) {
                for (int i = 0; i < revisedTransaction.get(levels).get(tid).size(); i++) {
                    Pair pair = revisedTransaction.get(levels).get(tid).get(i);
                    remainingUtility[levels] = remainingUtility[levels] - pair.utility;
                    UtilityList utilityListOfItem = mapItemToUtilityList.get(pair.item+"");
                    Element element = new Element(pair.utility, remainingUtility[levels]);
                    if (utilityListOfItem != null) {
                        utilityListOfItem.addElement(tid, element);
                    }
                }
            }
        }
        taxonomy.MapdataParent.entrySet().forEach((entry) -> {
            Integer key = entry.getKey();
            Integer value = entry.getValue();
            if (mapItemToUtilityList.get(key.toString()) != null && mapItemToUtilityList.get(value.toString()) != null) {
                if (mapItemToChildren.get(value) == null) {
                    mapItemToChildren.put(value, new ArrayList<>());
                }
                mapItemToChildren.get(value).add(key);
            }
            else{
                //System.out.println(key);
                
            }

        });

        System.out.println("Constructing utility lists...");
        for (int i = 0; i < maxLevel; i++) {
        	HashSet<Integer> set = new HashSet<>();
        	itemPromissingEachLevel.add(set);
            List<UtilityList> UtilityListOfILevel = new ArrayList<>();
            if (i==maxLevel-1) {
            	for (Integer item : mapItemToTWU.keySet()) {
                    if (mapItemToTWU.get(item) >= minUtility) { // if the item is promising (TWU >= minutility)
                        if (mapItemToLevel.get(item) == i + 1) {
                            UtilityList uList = mapItemToUtilityList.get(item.toString()); // create an empty Utility List that we will
                            UtilityListOfILevel.add(uList);
                        }
                    }
                }
            	listOfUtilityLists.add(UtilityListOfILevel);  
            	Collections.sort(listOfUtilityLists.get(i), new Comparator<UtilityList>() {
                    public int compare(UtilityList o1, UtilityList o2) {
                        return compareItems(o1.nameOfItemset[0], o2.nameOfItemset[0]); // compare the TWU of the items
                    }
                });
			}else {
				listOfUtilityLists.add(UtilityListOfILevel);  
			}
            

                     
        }

        System.out.println("Mining...");
        MemoryLogger.getInstance().checkMemory(); // check the memory usage

        
        
        /* ==== SEQUENTIAL CODE HERE */
        for (int i = maxLevel-1; i >=0; i--) {
            fhm(null, listOfUtilityLists.get(i), minUtility);
            if(i>0) {
            	for (int itemParent : itemPromissingEachLevel.get(i)) {
    				for (Integer item : mapItemToChildren.get(itemParent)) {
    					if (mapItemToUtilityList.get(item.toString())!=null) {
    						listOfUtilityLists.get(i-1).add(mapItemToUtilityList.get(item.toString()));
    					}
    				}
    			}
            	Collections.sort(listOfUtilityLists.get(i-1), new Comparator<UtilityList>() {
                    public int compare(UtilityList o1, UtilityList o2) {
                        return compareItems(o1.nameOfItemset[0], o2.nameOfItemset[0]); // compare the TWU of the items
                    }
                });
            }
            
        }

        MemoryLogger.getInstance().checkMemory(); // check the memory usage again and close the file.
        timerStop = System.currentTimeMillis(); // record end time

        System.out.println("Done.");
    }
    
    public String ConvertName(ArrayList<Integer> list) {
    	Collections.sort(list, new Comparator<Integer>() {
            public int compare(Integer o1, Integer o2) {
                return compareItems(o1, o2); // compare the TWU of the items
            }
        });
    	String s = "";
    	for (Integer integer : list) {
			s+=integer+" ";
		}
    	return s;
    }

    // Method to compare items by their TWU
    private int compareItems(int item1, int item2) {
        int compare = (int) (mapItemToTWU.get(item1) - mapItemToTWU.get(item2));
        // if the same, use the lexical order otherwise use the TWU
        return (compare == 0) ? item1 - item2 : compare;
    }

    // FHM algorithm
    private void fhm(UtilityList pUL, List<UtilityList> ULs, Double minUtility)
            throws IOException {

        // For each extension X of prefix P
        for (int i = 0; i < ULs.size(); i++) {
            UtilityList X = ULs.get(i);
            candidateCount++;
            // If pX is a high utility itemset. we save the itemset: pX
            int lvl = mapItemToLevel.get(X.nameOfItemset[0])-1;
            if (X.sumIutils >= minUtility) {
            	for (int item : X.nameOfItemset) {
            		itemPromissingEachLevel.get(lvl).add(item);
				}
                // System.out.println(X.elements.size());
            	mapItemToUtilityList.put(X.getName(), X);
            	writeOut(X); // output
            }
            if (X.sumIutils + X.sumRutils >= minUtility) {
                // This list will contain the utility lists of pX extensions.
                List<UtilityList> exULs = new ArrayList<>();
                for (int j = i + 1; j < ULs.size(); j++) {
                    UtilityList Y = ULs.get(j);
                    
                    UtilityList temp = construct(pUL, X, Y, minUtility);
                    if (temp != null) {
                        exULs.add(temp);
                    }
                }

                // We create new prefix pX
                //itemsetBuffer[prefixLength] = X.item;

                // We make a recursive call to discover all itemsets with the prefix pXY
                fhm(X, exULs, minUtility);
            }
        }
        MemoryLogger.getInstance().checkMemory();
    }

    // construct a utility list
    private UtilityList construct(UtilityList P, UtilityList px, UtilityList py, Double minUtility) {
        // create an empy utility list for pXY
        UtilityList pxyUL = new UtilityList(px.nameOfItemset,py.nameOfItemset[py.nameOfItemset.length-1]);
        double totalUtility = px.sumIutils + px.sumRutils;
        for (Map.Entry<Integer, Element> entry : px.elements.entrySet()) {
            Integer tid = entry.getKey();
            Element ex = entry.getValue();
            Element ey = py.elements.get(tid);
            if (ey == null) {
                totalUtility -= (ex.iutils + ex.rutils);
                if (totalUtility < minUtility) {
                    return null;
                }
                continue;
            }
            if (P == null) {
                // Create the new element
                Element eXY = new Element(ex.iutils + ey.iutils, ey.rutils);
                // add the new element to the utility list of pXY
                pxyUL.addElement(tid, eXY);

            } else {
                Element e = P.elements.get(tid);
                if (e != null) {
                    Element eXY = new Element(ex.iutils + ey.iutils - e.iutils, ey.rutils);
                    pxyUL.addElement(tid, eXY);
                }
            }
        }
        // return the utility list of pXY.
        return pxyUL;
    }

    // get maximum level from the taxonomy
    private static Integer getMaxLevel(Map<Integer, Integer> map) {
        if (map == null) {
            return null;
        }
        int length = map.size();
        Collection<Integer> c = map.values();
        Object[] obj = c.toArray();
        Arrays.sort(obj);
        return Integer.parseInt(obj[length - 1].toString());
    }

    private void writeOut(UtilityList X) throws IOException {
        patternCount++; // increase the number of high utility itemsets found
        StringBuilder buffer = new StringBuilder();
        // append the prefix
        int Name[] = X.nameOfItemset;
        for (int i = 0; i < Name.length; i++) {
            buffer.append(Name[i]);
            buffer.append(' ');
        }
        String parent = ConvertName(X.getParent(taxonomy));
        // append the utility value
        buffer.append(" #UTIL: ");
        buffer.append(X.sumIutils);
        buffer.append(" #RUTIL: ");
        buffer.append(X.sumRutils);
        buffer.append("-----Parent: ");
        buffer.append(parent);
        buffer.append(" #UTILPARENT: ");
        if (mapItemToLevel.get(X.nameOfItemset[0])!=maxLvl) {
        	buffer.append(mapItemToUtilityList.get(parent).sumIutils);
		}
        
        // write to file
       // System.out.println(buffer.toString());
    }

    // print statistics
    public void printStats() throws IOException {
    	 System.out.println("=============  "+this.getClass().getName()+" =============");
        System.out.println(" Given minutil     : " + minUtil);
        System.out.println(" Approx runtime    : " + (timerStop - timerStart) + " ms");
        System.out.println(" Approx memory used: " + MemoryLogger.getInstance().getMaxMemory() + " MB");
        System.out.println(" MLHUIs found      : " + patternCount);
        System.out.println(" Candidate count   : " + candidateCount);
        System.out.println("=======================================================");
    }

}
