package com.jiabo.thread.day04;

import java.util.concurrent.TimeUnit;

/**
 * 现象描述：a、b两个线程访问
 * <p>
 * 两个都是同步方法，先打印邮件还是短信？-------------先邮件再短信，共用一个对象锁。
 * sendEmail()休眠3秒，先打印邮件还是短信？----------先邮件再短信，共用一个对象锁。
 * 添加一个普通的hello方法，先打印普通方法还是邮件？------先hello，再邮件。
 * 两部手机，一个发短信，一个发邮件，先打印邮件还是短信？----先短信后邮件 资源没有争抢，不是同一个对象锁。
 * 两个静态同步方法，一部手机，先打印邮件还是短信？-----先邮件再短信，共用一个类锁。
 * 两个静态同步，两部手机，一个发短信，一个发邮件，先打印邮件还是短信？-----先邮件后短信，共用一个类锁。
 * 邮件静态同步，短信普通同步，先打印邮件还是短信？---先短信再邮件，一个类锁一个对象锁。
 * 邮件静态同步，短信普通同步，两部手机，先打印邮件还是短信？------先短信后邮件，一个类锁一个对象锁。
 *
 *
 *
 */
public class PhoneDemoSynchronized {
    public synchronized void sendEmail() {
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("------sendEmail");
    }

    public synchronized void sendSMS() {
        System.out.println("------sendSMS");
    }

    public void hello() {
        System.out.println("------hello");
    }

    public static synchronized void sendEmailStatic() {
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("------sendEmailStatic");
    }

    public static synchronized void sendSMSStatic() {
        System.out.println("------sendSMSStatic");
    }

    public static void main(String[] args) {
//        两个都是同步方法，先打印邮件还是短信？-------------先邮件再短信，共用一个对象锁。
//        sendEmail()休眠3秒，先打印邮件还是短信？----------先邮件再短信，共用一个对象锁。
//        添加一个普通的hello方法，先打印普通方法还是邮件？------先hello，再邮件。
        System.out.println("-----a and b");
        PhoneDemoSynchronized phone = new PhoneDemoSynchronized();
        new Thread(phone::sendEmail, "a").start();
        try {
            TimeUnit.MILLISECONDS.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new Thread(phone::sendSMS, "b").start();
        phone.hello();
//        两部手机，一个发短信，一个发邮件，先打印邮件还是短信？----先短信后邮件 资源没有争抢，不是同一个对象锁。
        System.out.println("-----c and d");
        PhoneDemoSynchronized phone1 = new PhoneDemoSynchronized();
        PhoneDemoSynchronized phone2 = new PhoneDemoSynchronized();
        new Thread(phone1::sendSMS, "c").start();
        new Thread(phone2::sendEmail, "d").start();
//        两个静态同步方法，一部手机，先打印邮件还是短信？-----先邮件再短信，共用一个类锁。
        System.out.println("-----e and f");
        new Thread(PhoneDemoSynchronized::sendEmailStatic, "e").start();
        new Thread(PhoneDemoSynchronized::sendSMSStatic, "f").start();
////        两个静态同步，两部手机，一个发短信，一个发邮件，先打印邮件还是短信？-----先邮件后短信，共用一个类锁。
        System.out.println("-----g and h");
        new Thread(PhoneDemoSynchronized::sendEmailStatic, "g").start();
        new Thread(PhoneDemoSynchronized::sendSMSStatic, "h").start();
////        邮件静态同步，短信普通同步，先打印邮件还是短信？---先短信再邮件，一个类锁一个对象锁。
        System.out.println("-----i and j");
        new Thread(PhoneDemoSynchronized::sendEmailStatic, "i").start();
        new Thread(phone::sendSMS, "j").start();
////        邮件静态同步，短信普通同步，两部手机，先打印邮件还是短信？------先短信后邮件，一个类锁一个对象锁。
        System.out.println("-----k and l");
        new Thread(PhoneDemoSynchronized::sendEmailStatic, "k").start();
        new Thread(phone::sendSMS, "l").start();
    }

}
