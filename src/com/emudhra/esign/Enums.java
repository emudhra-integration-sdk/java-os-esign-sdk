/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.emudhra.esign;

/**
 *
 * @author 21032
 */
public class Enums {

    public enum Status {
        Failure(0),
        Success(1);

        private final int val;
        private Status(int val) {
            this.val = val;
        }
        public int getVal(){
            return val;
        }
    }

    public enum ImageType{
        SVG, Other
    }

    public enum DocumentType{
        PDF, Other
    }
}
