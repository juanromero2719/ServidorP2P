/**
 *
 * @author Estudiante_MCA
 */

package com.mycompany.chatserverproject;

public interface Subject {
    void addObserver(MessageObserver observer);
    void removeObserver(MessageObserver observer);
    void notifyObservers(String message);
}